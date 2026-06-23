package com.lms.service;

import com.lms.model.DocumentChunk;
import com.lms.repository.DocumentChunkRepository;
import com.lms.service.ai.provider.EmbeddingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class HybridRetrievalService {

    private static final Logger LOG = LoggerFactory.getLogger(HybridRetrievalService.class);

    private final DocumentChunkRepository chunkRepository;
    private final EmbeddingProvider        embeddingProvider;
    private final Optional<VectorStore>    vectorStore;
    private final MeterRegistry            meterRegistry;
    private final double                   alpha = 0.7; // 70% vector weight, 30% keyword weight

    @org.springframework.beans.factory.annotation.Value("${app.ai.fast-mode:false}")
    private boolean fastMode;

    @Autowired
    public HybridRetrievalService(DocumentChunkRepository chunkRepository,
                                  EmbeddingProvider embeddingProvider,
                                  Optional<VectorStore> vectorStore,
                                  MeterRegistry meterRegistry) {
        this.chunkRepository   = chunkRepository;
        this.embeddingProvider = embeddingProvider;
        this.vectorStore       = vectorStore;
        this.meterRegistry     = meterRegistry;
    }

    /**
     * Hybrid retrieval using embeddings (semantic) and simple keyword frequency.
     */
    public List<DocumentChunk> retrieve(String userId, String query, List<String> sourceIds, int limit) {
        if (fastMode) {
            limit = Math.min(limit, 2);
        }

        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            // 1. Get Query Vector Embedding
            List<Double> queryVector = embeddingProvider.getEmbedding(query);

            // 2. If VectorStore is enabled, use it for semantic search
            if (vectorStore.isPresent()) {
                try {
                    return vectorStore.orElseThrow(() -> new IllegalStateException("VectorStore not present")).search(queryVector, sourceIds, userId, limit);
                } catch (Exception e) {
                    LOG.warn("VectorStore search failed, falling back to MongoDB in-memory search: {}", e.getMessage());
                }
            }

            // Fallback: MongoDB candidates + in-memory scoring
            List<DocumentChunk> candidates;
            if (sourceIds != null && !sourceIds.isEmpty()) {
                candidates = chunkRepository.findBySourceIdIn(sourceIds);
            } else {
                candidates = chunkRepository.findByUserId(userId);
            }

            if (candidates.isEmpty()) {
                LOG.info("No candidates found in DB for hybrid retrieval. userId={}, sources={}", userId, sourceIds);
                return Collections.emptyList();
            }

            // Tokenize Query for Keyword Search
            String[] queryTerms = query.toLowerCase()
                    .replaceAll("[^a-z0-9\\s]", " ")
                    .split("\\s+");
            List<String> activeTerms = Arrays.stream(queryTerms)
                    .filter(term -> term.length() > 2)
                    .collect(Collectors.toList());

            // Score candidates
            List<ScoredChunk> scoredList = new ArrayList<>();
            for (DocumentChunk chunk : candidates) {
                double semanticScore = cosineSimilarity(queryVector, chunk.getEmbedding());
                double keywordScore  = calculateKeywordScore(chunk.getText(), activeTerms);

                // Linear combination
                double hybridScore = (alpha * semanticScore) + ((1.0 - alpha) * keywordScore);
                scoredList.add(new ScoredChunk(chunk, hybridScore));
            }

            // Sort and return top-K
            return scoredList.stream()
                    .sorted(Comparator.comparingDouble(ScoredChunk::getScore).reversed())
                    .limit(limit)
                    .map(ScoredChunk::getChunk)
                    .collect(Collectors.toList());
        } finally {
            sample.stop(meterRegistry.timer("ai.retrieval.time"));
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────

    private double cosineSimilarity(List<Double> vecA, List<Double> vecB) {
        if (vecA == null || vecB == null || vecA.isEmpty() || vecB.isEmpty() || vecA.size() != vecB.size()) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA      = 0.0;
        double normB      = 0.0;

        for (int i = 0; i < vecA.size(); i++) {
            double a = vecA.get(i);
            double b = vecB.get(i);
            dotProduct += a * b;
            normA      += a * a;
            normB      += b * b;
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        double sim = dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
        // Clamp to positive range [0, 1]
        return Math.max(0.0, sim);
    }

    private double calculateKeywordScore(String text, List<String> terms) {
        if (terms.isEmpty()) {
            return 0.0;
        }

        String lowerText = text.toLowerCase();
        double matches = 0.0;
        for (String term : terms) {
            if (lowerText.contains(term)) {
                matches += 1.0;
            }
        }

        // Normalize keyword score by total terms
        return matches / terms.size();
    }

    private static class ScoredChunk {
        private final DocumentChunk chunk;
        private final double        score;

        public ScoredChunk(DocumentChunk chunk, double score) {
            this.chunk = chunk;
            this.score = score;
        }

        public DocumentChunk getChunk() { return chunk; }
        public double        getScore() { return score; }
    }
}
