package com.lms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.model.DocumentChunk;
import com.lms.model.Source;
import com.lms.repository.DocumentChunkRepository;
import com.lms.repository.SourceRepository;
import jakarta.annotation.PreDestroy;
import com.lms.service.ai.provider.EmbeddingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.scheduling.annotation.Async;
import org.springframework.beans.factory.annotation.Qualifier;

@Service
public class IngestionPipeline {

    private static final Logger LOG = LoggerFactory.getLogger(IngestionPipeline.class);

    private final SourceRepository        sourceRepository;
    private final DocumentChunkRepository chunkRepository;
    private final DocumentProcessor        documentProcessor;
    private final EmbeddingProvider        embeddingProvider;
    private final com.lms.service.ai.AIOrchestratorService aiOrchestratorService;
    private final VectorStore              vectorStore;
    private final OCRService               ocrService;
    private final ExecutorService          executor;
    private final ObjectMapper             objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final java.util.concurrent.Executor embeddingTaskExecutor;

    @Autowired
    public IngestionPipeline(SourceRepository sourceRepository,
                             DocumentChunkRepository chunkRepository,
                             DocumentProcessor documentProcessor,
                             EmbeddingProvider embeddingProvider,
                             com.lms.service.ai.AIOrchestratorService aiOrchestratorService,
                             OCRService ocrService,
                             ObjectMapper objectMapper,
                             ApplicationEventPublisher eventPublisher,
                             @Qualifier("embeddingTaskExecutor") java.util.concurrent.Executor embeddingTaskExecutor,
                             @Autowired(required = false) VectorStore vectorStore) {
        this.sourceRepository  = sourceRepository;
        this.chunkRepository   = chunkRepository;
        this.documentProcessor = documentProcessor;
        this.embeddingProvider = embeddingProvider;
        this.aiOrchestratorService = aiOrchestratorService;
        this.ocrService        = ocrService;
        this.vectorStore       = vectorStore;
        this.objectMapper      = objectMapper;
        this.eventPublisher    = eventPublisher;
        this.embeddingTaskExecutor = embeddingTaskExecutor;
        // Named threads for readable thread dumps and metrics
        ThreadFactory namedFactory = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(0);
            @Override public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "ingestion-worker-" + counter.incrementAndGet());
                t.setDaemon(true); // Don't block JVM shutdown
                return t;
            }
        };
        this.executor = Executors.newFixedThreadPool(4, namedFactory);
    }

    /** Graceful shutdown — wait up to 30s for in-flight ingestions to complete. */
    @PreDestroy
    public void shutdown() {
        LOG.info("IngestionPipeline shutting down — waiting for in-flight tasks (max 30s)...");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                LOG.warn("IngestionPipeline executor did not terminate within 30s — forcing shutdown.");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    @Async("ingestionTaskExecutor")
    public CompletableFuture<Source> ingest(Source savedSource, InputStream inputStream, String filename, String contentType) {
        LOG.info("UPLOAD STARTED");
        String userId = savedSource.getUserId();
        if (savedSource.getMetadata() == null) {
            savedSource.setMetadata(new HashMap<>());
        }
        savedSource.getMetadata().put("status", "PROCESSING");
        savedSource.getMetadata().put("progress", 10);
        sourceRepository.save(savedSource);
        try (InputStream is = inputStream) {
            LOG.info("Processing file in ingestion pipeline: {} (sourceId: {})", filename, savedSource.getId());

            List<DocumentProcessor.Chunk> pages;
            
            // OCR check
            if ("IMAGE".equals(savedSource.getType())) {
                String extractedText = ocrService.extractTextFromImage(is);
                pages = List.of(new DocumentProcessor.Chunk(extractedText, 1));
            } else {
                // Parse document text page-by-page (internally runs scanned PDF check with 150 DPI OCR)
                pages = documentProcessor.processDocument(is, filename, contentType);
            }
            LOG.info("TEXT EXTRACTION COMPLETE");

            String extractedText = pages.stream()
                    .map(DocumentProcessor.Chunk::getText)
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.joining("\n"));

            LOG.info("FILE={}", filename);
            LOG.info("EXTRACTED_TEXT_LENGTH={}", extractedText.length());
            LOG.info("EXTRACTED_TEXT_PREVIEW={}", extractedText.substring(0, Math.min(1000, extractedText.length())));

            if (extractedText.trim().isEmpty()) {
                LOG.error("EXTRACTED_TEXT IS EMPTY OR NULL for file: {}", filename);
                throw new RuntimeException("Document extraction produced no text content.");
            }

            // Save extractedText directly on the Source entity (Stage 1 complete)
            savedSource.setExtractedText(extractedText);
            savedSource.getMetadata().put("progress", 25);
            savedSource.getMetadata().put("pagesCount", pages.size());
            savedSource = sourceRepository.save(savedSource);
            
            // Publish events immediately so that AI orchestrator is triggered using the raw extractedText
            eventPublisher.publishEvent(new com.lms.model.DocumentIngestedEvent(this, savedSource.getId(), userId));
            eventPublisher.publishEvent(new com.lms.events.DocumentUploadedEvent(this, savedSource.getId(), userId));
            
            // Offload Stage 2: chunking, embedding generation and vector database upsert to embeddingTaskExecutor
            final Source finalSource = savedSource;
            final List<DocumentProcessor.Chunk> finalPages = pages;
            CompletableFuture.runAsync(() -> {
                try {
                    LOG.info("Stage 2 Background Ingestion started for sourceId: {}", finalSource.getId());
                    List<DocumentProcessor.Chunk> overlapping = documentProcessor.createOverlappingChunks(finalPages, 800, 150);
                    LOG.info("TOTAL_CHUNKS={}", overlapping.size());

                    if (overlapping.isEmpty()) {
                        LOG.warn("No chunks created in Stage 2 for sourceId: {}", finalSource.getId());
                        return;
                    }

                    List<DocumentChunk> dbChunks = new java.util.ArrayList<>();
                    String now = java.time.Instant.now().toString();
                    for (int i = 0; i < overlapping.size(); i++) {
                        DocumentProcessor.Chunk rawChunk = overlapping.get(i);
                        List<Double> embedding = embeddingProvider.getEmbedding(rawChunk.getText());
                        DocumentChunk dbChunk = new DocumentChunk(
                                finalSource.getId(),
                                userId,
                                rawChunk.getText(),
                                embedding,
                                rawChunk.getPageNumber(),
                                i,
                                now
                        );
                        dbChunks.add(dbChunk);
                    }
                    
                    // Batch write chunks to mongo
                    chunkRepository.saveAll(dbChunks);
                    
                    if (vectorStore != null) {
                        vectorStore.upsertChunks(dbChunks);
                    }

                    // Async metadata extraction via LLM
                    Map<String, Object> extractedMetadata = extractMetadataViaLlm(finalPages);

                    Source currentSource = sourceRepository.findById(finalSource.getId()).orElse(finalSource);
                    currentSource.getMetadata().put("chunksCount", overlapping.size());
                    currentSource.getMetadata().putAll(extractedMetadata);
                    if (!"FAILED".equals(currentSource.getMetadata().get("status"))) {
                        currentSource.getMetadata().put("progress", 50);
                    }
                    sourceRepository.save(currentSource);
                    LOG.info("Stage 2 Background Ingestion complete for sourceId: {}", finalSource.getId());
                } catch (Exception e) {
                    LOG.error("Failed in Stage 2 Background Ingestion for sourceId: {}", finalSource.getId(), e);
                }
            }, embeddingTaskExecutor);

            return CompletableFuture.completedFuture(savedSource);

        } catch (Throwable e) {
            LOG.error("Failed to ingest file: {}", filename, e);
            try {
                savedSource.getMetadata().put("status", "FAILED");
                savedSource.getMetadata().put("error", String.valueOf(e.getMessage()));
                sourceRepository.save(savedSource);
            } catch (Exception saveEx) {
                LOG.error("CRITICAL: could not persist failed status for source {}", savedSource.getId(), saveEx);
            }
            CompletableFuture<Source> failed = new CompletableFuture<>();
            failed.completeExceptionally(new RuntimeException("Document Ingestion failed", e));
            return failed;
        }
    }

    /**
     * Start URL ingestion pipeline asynchronously. Supports YouTube link stubs.
     */
    @Async("ingestionTaskExecutor")
    public CompletableFuture<Source> ingestUrl(Source savedSource, String urlString) {
        if (savedSource.getMetadata() == null) {
            savedSource.setMetadata(new HashMap<>());
        }
        savedSource.getMetadata().put("status", "PROCESSING");
        savedSource.getMetadata().put("progress", 10);
        sourceRepository.save(savedSource);
        String type = savedSource.getType();
        String userId = savedSource.getUserId();
        try {
            LOG.info("Processing URL in ingestion pipeline: {} (sourceId: {})", urlString, savedSource.getId());

            List<DocumentProcessor.Chunk> pages;
            if ("YOUTUBE".equals(type)) {
                pages = processYouTubeTranscript(urlString);
            } else {
                pages = documentProcessor.processUrl(urlString);
            }
            LOG.info("TEXT EXTRACTION COMPLETE");

            String extractedText = pages.stream()
                    .map(DocumentProcessor.Chunk::getText)
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.joining("\n"));

            LOG.info("FILE={}", urlString);
            LOG.info("EXTRACTED_TEXT_LENGTH={}", extractedText.length());
            LOG.info("EXTRACTED_TEXT_PREVIEW={}", extractedText.substring(0, Math.min(1000, extractedText.length())));

            if (extractedText.trim().isEmpty()) {
                LOG.error("EXTRACTED_TEXT IS EMPTY OR NULL for url: {}", urlString);
                throw new RuntimeException("URL extraction produced no text content.");
            }

            // Save extractedText directly on the Source entity (Stage 1 complete)
            savedSource.setExtractedText(extractedText);
            savedSource.getMetadata().put("progress", 25);
            savedSource.getMetadata().put("pagesCount", pages.size());
            savedSource = sourceRepository.save(savedSource);

            // Publish events immediately
            eventPublisher.publishEvent(new com.lms.model.DocumentIngestedEvent(this, savedSource.getId(), userId));
            eventPublisher.publishEvent(new com.lms.events.DocumentUploadedEvent(this, savedSource.getId(), userId));

            // Offload Stage 2
            final Source finalSource = savedSource;
            final List<DocumentProcessor.Chunk> finalPages = pages;
            CompletableFuture.runAsync(() -> {
                try {
                    LOG.info("Stage 2 Background Ingestion started for URL sourceId: {}", finalSource.getId());
                    List<DocumentProcessor.Chunk> overlapping = documentProcessor.createOverlappingChunks(finalPages, 800, 150);
                    LOG.info("TOTAL_CHUNKS={}", overlapping.size());

                    if (overlapping.isEmpty()) {
                        LOG.warn("No chunks created in Stage 2 for sourceId: {}", finalSource.getId());
                        return;
                    }

                    List<DocumentChunk> dbChunks = new java.util.ArrayList<>();
                    String now = java.time.Instant.now().toString();
                    for (int i = 0; i < overlapping.size(); i++) {
                        DocumentProcessor.Chunk rawChunk = overlapping.get(i);
                        List<Double> embedding = embeddingProvider.getEmbedding(rawChunk.getText());
                        DocumentChunk dbChunk = new DocumentChunk(
                                finalSource.getId(),
                                userId,
                                rawChunk.getText(),
                                embedding,
                                rawChunk.getPageNumber(),
                                i,
                                now
                        );
                        dbChunks.add(dbChunk);
                    }
                    
                    // Batch write chunks to mongo
                    chunkRepository.saveAll(dbChunks);
                    
                    if (vectorStore != null) {
                        vectorStore.upsertChunks(dbChunks);
                    }

                    // Async metadata extraction via LLM
                    Map<String, Object> extractedMetadata = extractMetadataViaLlm(finalPages);

                    Source currentSource = sourceRepository.findById(finalSource.getId()).orElse(finalSource);
                    currentSource.getMetadata().put("chunksCount", overlapping.size());
                    currentSource.getMetadata().putAll(extractedMetadata);
                    if (!"FAILED".equals(currentSource.getMetadata().get("status"))) {
                        currentSource.getMetadata().put("progress", 50);
                    }
                    sourceRepository.save(currentSource);
                    LOG.info("Stage 2 Background Ingestion complete for URL sourceId: {}", finalSource.getId());
                } catch (Exception e) {
                    LOG.error("Failed in Stage 2 Background Ingestion for URL sourceId: {}", finalSource.getId(), e);
                }
            }, embeddingTaskExecutor);

            return CompletableFuture.completedFuture(savedSource);

        } catch (Throwable e) {
            LOG.error("Failed to ingest URL: {}", urlString, e);
            try {
                savedSource.getMetadata().put("status", "FAILED");
                savedSource.getMetadata().put("error", String.valueOf(e.getMessage()));
                sourceRepository.save(savedSource);
            } catch (Exception saveEx) {
                LOG.error("CRITICAL: could not persist failed status for source {}", savedSource.getId(), saveEx);
            }
            CompletableFuture<Source> failed = new CompletableFuture<>();
            failed.completeExceptionally(new RuntimeException("URL Ingestion failed", e));
            return failed;
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────

    private Map<String, Object> extractMetadataViaLlm(List<DocumentProcessor.Chunk> pages) {
        Map<String, Object> metadata = new HashMap<>();
        if (pages.isEmpty()) return metadata;

        // Take a snippet of first page text for analysis
        String textSnippet = pages.get(0).getText();
        if (textSnippet.length() > 1500) {
            textSnippet = textSnippet.substring(0, 1500);
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("Analyze this text segment from a study source and extract metadata variables.\n");
        prompt.append("Snippet:\n").append(textSnippet).append("\n\n");
        prompt.append("Format response ONLY as a single valid JSON object containing the keys: 'author', 'title', 'keywords' (list of strings), and 'summary' (brief 2-sentence description). Do not include markdown code block styling or text outside the JSON.\n");

        try {
            LOG.info("STEP 5 AI Request Started");
            String response = aiOrchestratorService.generate(prompt.toString(), Map.of());
            LOG.info("STEP 6 AI Response Received");
            String cleanJson = response.replaceAll("(?s)^.*?\\{\\s*\"", "{\"")
                    .replaceAll("(?s)\"\\s*\\}.*?$", "\"}").trim();

            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(cleanJson, Map.class);
            metadata.put("author", parsed.getOrDefault("author", "Unknown Author"));
            metadata.put("extractedTitle", parsed.getOrDefault("title", "Untitled Document"));
            metadata.put("keywords", parsed.getOrDefault("keywords", List.of()));
            metadata.put("summary", parsed.getOrDefault("summary", "No summary extracted."));
        } catch (Exception e) {
            LOG.warn("Failed to extract LLM metadata stubs: {}", e.getMessage());
            metadata.put("author", "Unknown Author");
            metadata.put("summary", "Study document containing educational references.");
        }

        return metadata;
    }

    private List<DocumentProcessor.Chunk> processYouTubeTranscript(String youtubeUrl) {
        LOG.info("Processing YouTube Video transcripts: {}", youtubeUrl);
        throw new UnsupportedOperationException("YouTube transcript extraction is not natively implemented yet. Requires external transcription service.");
    }

    private String determineType(String filename, String contentType) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf") || "application/pdf".equals(contentType)) return "PDF";
        if (lower.endsWith(".docx")) return "DOCX";
        if (lower.endsWith(".pptx") || lower.endsWith(".ppt")) return "PPTX";
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
            (contentType != null && contentType.startsWith("image/"))) return "IMAGE";
        return "TXT";
    }

    private String extractDomain(String urlString) {
        try {
            java.net.URI uri = java.net.URI.create(urlString);
            String host = uri.getHost();
            return host != null ? host : urlString;
        } catch (Exception e) {
            return urlString;
        }
    }
}
