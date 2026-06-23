package com.lms.service;

import com.lms.model.DocumentChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RerankingService {

    private static final Logger LOG = LoggerFactory.getLogger(RerankingService.class);

    /**
     * Reranks retrieved candidate chunks based on phrase proximity, term density, and structural match.
     */
    public List<DocumentChunk> rerank(List<DocumentChunk> candidates, String query) {
        if (candidates == null || candidates.isEmpty() || query == null || query.isBlank()) {
            return candidates;
        }

        LOG.info("Running second-pass statistical reranking on {} candidate chunks", candidates.size());

        String cleanedQuery = query.toLowerCase().replaceAll("[^a-z0-9\\s]", " ");
        String[] terms = cleanedQuery.split("\\s+");

        // Build phrase bigrams if query has multiple words
        List<String> bigrams = new ArrayList<>();
        for (int i = 0; i < terms.length - 1; i++) {
            if (terms[i].length() > 2 && terms[i+1].length() > 2) {
                bigrams.add(terms[i] + " " + terms[i+1]);
            }
        }

        List<RerankedItem> scored = new ArrayList<>();
        for (DocumentChunk chunk : candidates) {
            double score = computeRelevanceDensity(chunk.getText().toLowerCase(), terms, bigrams);
            scored.add(new RerankedItem(chunk, score));
        }

        // Sort by dense phrase match score descending
        return scored.stream()
                .sorted(Comparator.comparingDouble(RerankedItem::getScore).reversed())
                .map(RerankedItem::getChunk)
                .collect(Collectors.toList());
    }

    private double computeRelevanceDensity(String text, String[] terms, List<String> bigrams) {
        double score = 0.0;

        // 1. Density score: count total hits of individual query terms
        double termHits = 0;
        for (String term : terms) {
            if (term.length() > 2 && text.contains(term)) {
                termHits += 1.0;
            }
        }
        score += (termHits / Math.max(1, terms.length)) * 2.0;

        // 2. Phrase proximity score: check for consecutive word bigram matches (higher weight)
        double bigramHits = 0;
        for (String bigram : bigrams) {
            if (text.contains(bigram)) {
                bigramHits += 1.0;
            }
        }
        if (!bigrams.isEmpty()) {
            score += (bigramHits / bigrams.size()) * 4.0;
        }

        // 3. Absolute matching: exact query matches as a full substring (highest weight)
        String fullPhrase = String.join(" ", terms);
        if (fullPhrase.length() > 5 && text.contains(fullPhrase)) {
            score += 5.0;
        }

        return score;
    }

    private static class RerankedItem {
        private final DocumentChunk chunk;
        private final double        score;

        public RerankedItem(DocumentChunk chunk, double score) {
            this.chunk = chunk;
            this.score = score;
        }

        public DocumentChunk getChunk() { return chunk; }
        public double        getScore() { return score; }
    }
}
