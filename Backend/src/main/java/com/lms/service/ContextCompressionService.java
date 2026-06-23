package com.lms.service;

import com.lms.model.DocumentChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ContextCompressionService {

    private static final Logger LOG = LoggerFactory.getLogger(ContextCompressionService.class);

    private static final int MAX_CONTEXT_CHARACTERS = 4000;

    /**
     * Compress retrieved document chunks into a condensed, token-optimized format.
     */
    public List<DocumentChunk> compress(List<DocumentChunk> chunks, String query) {
        if (chunks == null || chunks.isEmpty()) {
            return chunks;
        }

        LOG.info("Compressing {} chunks to fit context budget (Limit: {} chars)", chunks.size(), MAX_CONTEXT_CHARACTERS);

        String cleanedQuery = query.toLowerCase().replaceAll("[^a-z0-9\\s]", " ");
        List<String> queryTerms = Arrays.stream(cleanedQuery.split("\\s+"))
                .filter(term -> term.length() > 2)
                .collect(Collectors.toList());

        List<DocumentChunk> compressedChunks = new ArrayList<>();
        int currentLength = 0;

        for (DocumentChunk chunk : chunks) {
            String compressedText = compressChunkText(chunk.getText(), queryTerms);
            
            // Check budget constraints
            if (currentLength + compressedText.length() > MAX_CONTEXT_CHARACTERS) {
                // If chunk is empty or first chunk exceeds limit, squeeze partial sentences
                int remainingBudget = MAX_CONTEXT_CHARACTERS - currentLength;
                if (remainingBudget > 100) {
                    String truncatedText = compressedText.substring(0, remainingBudget) + "... [Truncated to save tokens]";
                    DocumentChunk compressedChunk = new DocumentChunk(
                            chunk.getSourceId(),
                            chunk.getUserId(),
                            truncatedText,
                            chunk.getEmbedding(),
                            chunk.getPageNumber()
                    );
                    compressedChunks.add(compressedChunk);
                }
                break;
            }

            DocumentChunk compressedChunk = new DocumentChunk(
                    chunk.getSourceId(),
                    chunk.getUserId(),
                    compressedText,
                    chunk.getEmbedding(),
                    chunk.getPageNumber()
            );
            compressedChunks.add(compressedChunk);
            currentLength += compressedText.length();
        }

        return compressedChunks;
    }

    // ── Internals ─────────────────────────────────────────────────────────

    private String compressChunkText(String text, List<String> queryTerms) {
        if (text == null || text.isBlank() || queryTerms.isEmpty()) {
            return text;
        }

        // Split text into sentences
        String[] sentences = text.split("(?<=[.!?])\\s+");
        List<String> preservedSentences = new ArrayList<>();

        for (String sentence : sentences) {
            String lowerSentence = sentence.toLowerCase();
            boolean matches = false;
            
            // Keep sentence if it contains any query terms
            for (String term : queryTerms) {
                if (lowerSentence.contains(term)) {
                    matches = true;
                    break;
                }
            }

            if (matches) {
                preservedSentences.add(sentence.trim());
            }
        }

        // If no sentences match query terms, fallback to preserving the first 2 sentences
        if (preservedSentences.isEmpty()) {
            int preserveCount = Math.min(2, sentences.length);
            for (int i = 0; i < preserveCount; i++) {
                preservedSentences.add(sentences[i].trim());
            }
        }

        return String.join(" ", preservedSentences);
    }
}
