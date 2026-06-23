package com.lms.service;

import com.lms.model.DocumentChunk;
import com.lms.repository.DocumentChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import com.lms.service.ai.EmbeddingService;

@Service
public class RetrievalService {

    private static final Logger LOG = LoggerFactory.getLogger(RetrievalService.class);

    private final DocumentChunkRepository chunkRepository;
    private final EmbeddingService         embeddingService;
    private final VectorStore              vectorStore;

    @org.springframework.beans.factory.annotation.Value("${app.ai.fast-mode:false}")
    private boolean fastMode;

    @Autowired
    public RetrievalService(DocumentChunkRepository chunkRepository,
                            EmbeddingService embeddingService,
                            @Autowired(required = false) VectorStore vectorStore) {
        this.chunkRepository  = chunkRepository;
        this.embeddingService = embeddingService;
        this.vectorStore      = vectorStore;
    }

    /**
     * Retrieve the most relevant chunks for a user query.
     */
    public List<DocumentChunk> retrieve(String userId, String query, List<String> sourceIds, int limit) {
        if (fastMode) {
            limit = Math.min(limit, 2);
        }

        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        try {
            // Compute query vector
            List<Double> queryEmbedding = embeddingService.generateEmbedding(query);

            if (vectorStore != null) {
                LOG.info("Using True Vector Search to retrieve chunks");
                return vectorStore.search(queryEmbedding, sourceIds, userId, limit);
            }

            // Fallback to in-memory cosine similarity via MongoDB
            LOG.info("VectorStore not configured, falling back to in-memory cosine similarity via MongoDB");
            List<DocumentChunk> candidates;
            if (sourceIds != null && !sourceIds.isEmpty()) {
                candidates = chunkRepository.findBySourceIdIn(sourceIds);
            } else {
                candidates = chunkRepository.findByUserId(userId);
            }

            if (candidates.isEmpty()) {
                LOG.info("No text chunks found for user {} and sources {}", userId, sourceIds);
                return Collections.emptyList();
            }

            // Compute similarity scores
            List<ScoredChunk> scoredChunks = new ArrayList<>();
            for (DocumentChunk chunk : candidates) {
                double score = cosineSimilarity(queryEmbedding, chunk.getEmbedding());
                scoredChunks.add(new ScoredChunk(chunk, score));
            }

            // Sort by score descending and return top K
            return scoredChunks.stream()
                    .sorted(Comparator.comparingDouble(ScoredChunk::getScore).reversed())
                    .limit(limit)
                    .map(ScoredChunk::getChunk)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            LOG.warn("Semantic retrieval failed. Falling back to keyword search: {}", e.getMessage());
            
            List<DocumentChunk> candidates;
            if (sourceIds != null && !sourceIds.isEmpty()) {
                candidates = chunkRepository.findBySourceIdIn(sourceIds);
            } else {
                candidates = chunkRepository.findByUserId(userId);
            }
            return keywordFallbackSearch(query, candidates, limit);
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

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private List<DocumentChunk> keywordFallbackSearch(String query, List<DocumentChunk> candidates, int limit) {
        // Simple tokenize and search
        String[] queryTerms = query.toLowerCase().replaceAll("[^a-z0-9\\s]", "").split("\\s+");
        if (queryTerms.length == 0) {
            return candidates.stream().limit(limit).collect(Collectors.toList());
        }

        List<ScoredChunk> scored = new ArrayList<>();
        for (DocumentChunk chunk : candidates) {
            String text = chunk.getText().toLowerCase();
            double hits = 0;
            for (String term : queryTerms) {
                if (term.length() > 2 && text.contains(term)) {
                    hits += 1.0;
                }
            }
            scored.add(new ScoredChunk(chunk, hits));
        }

        return scored.stream()
                .filter(sc -> sc.getScore() > 0)
                .sorted(Comparator.comparingDouble(ScoredChunk::getScore).reversed())
                .limit(limit)
                .map(ScoredChunk::getChunk)
                .collect(Collectors.toList());
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
