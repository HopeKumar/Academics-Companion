package com.lms.controller;

import com.lms.model.DocumentChunk;
import com.lms.repository.DocumentChunkRepository;
import com.lms.service.ContentSanitizerService;
import com.lms.service.ai.provider.EmbeddingProvider;
import com.lms.service.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lms.config.FeaturesProperties;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping({"/admin", "/api/v1/admin"})
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private static final Logger LOG = LoggerFactory.getLogger(AdminController.class);

    private final DocumentChunkRepository chunkRepository;
    private final ContentSanitizerService sanitizerService;
    private final EmbeddingProvider embeddingProvider;
    private final VectorStore vectorStore;
    private final FeaturesProperties features;

    @Autowired
    public AdminController(DocumentChunkRepository chunkRepository,
                           ContentSanitizerService sanitizerService,
                           EmbeddingProvider embeddingProvider,
                           @Autowired(required = false) VectorStore vectorStore,
                           FeaturesProperties features) {
        this.chunkRepository = chunkRepository;
        this.sanitizerService = sanitizerService;
        this.embeddingProvider = embeddingProvider;
        this.vectorStore = vectorStore;
        this.features = features;
    }

    @PostMapping("/reindex")
    public ResponseEntity<?> reindexAll() {
        if (!features.isEmbeddingsEnabled()) {
            return ResponseEntity.ok(Map.of(
                "featureEnabled", false,
                "message", "Feature temporarily disabled"
            ));
        }
        long totalChunks = chunkRepository.count();
        LOG.info("REINDEX STARTED: {} total chunks to sanitize and re-embed.", totalChunks);

        CompletableFuture.runAsync(() -> {
            List<DocumentChunk> allChunks = chunkRepository.findAll();
            AtomicInteger success = new AtomicInteger(0);
            AtomicInteger failed = new AtomicInteger(0);
            AtomicInteger htmlDetected = new AtomicInteger(0);

            for (int i = 0; i < allChunks.size(); i++) {
                DocumentChunk chunk = allChunks.get(i);
                try {
                    String originalText = chunk.getText();
                    String cleanText = sanitizerService.sanitize(originalText);

                    // Log if HTML was actually found
                    if (!originalText.equals(cleanText)) {
                        htmlDetected.incrementAndGet();
                        if (htmlDetected.get() <= 5) {
                            LOG.info("REINDEX: HTML detected in chunk {} — before_len={}, after_len={}",
                                    chunk.getId(), originalText.length(), cleanText.length());
                        }
                    }

                    chunk.setText(cleanText);

                    List<Double> newEmbedding = embeddingProvider.getEmbedding(cleanText);
                    chunk.setEmbedding(newEmbedding);

                    chunkRepository.save(chunk);

                    if (vectorStore != null) {
                        vectorStore.upsertChunks(List.of(chunk));
                    }

                    success.incrementAndGet();

                    if ((i + 1) % 50 == 0) {
                        LOG.info("REINDEX PROGRESS: {}/{} chunks processed, {} sanitized, {} failed",
                                i + 1, allChunks.size(), htmlDetected.get(), failed.get());
                    }
                } catch (Exception e) {
                    failed.incrementAndGet();
                    LOG.error("REINDEX FAILURE: chunk_id={}, error={}", chunk.getId(), e.getMessage());
                }
            }
            LOG.info("REINDEX COMPLETED: total={}, success={}, html_cleaned={}, failed={}",
                    allChunks.size(), success.get(), htmlDetected.get(), failed.get());
        });

        return ResponseEntity.ok(Map.of(
                "message", "Re-indexing started in the background.",
                "totalChunks", totalChunks
        ));
    }
}
