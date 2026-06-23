package com.lms.service.ai;

import com.lms.dto.ai.DocumentChunk;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentChunkingService {

    private static final int MIN_CHUNK_SIZE = 800;
    private static final int MAX_CHUNK_SIZE = 1200;
    private static final int OVERLAP_SIZE = 150;

    public List<DocumentChunk> chunkDocument(String text, String sourceId) {
        List<DocumentChunk> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }

        // Split by paragraphs
        String[] paragraphs = text.split("\\r?\\n\\r?\\n+");
        StringBuilder currentChunk = new StringBuilder();
        int chunkIndex = 0;

        for (int i = 0; i < paragraphs.length; i++) {
            String para = paragraphs[i].trim();
            if (para.isEmpty()) continue;

            // If the paragraph fits exactly or current chunk is big enough
            if (currentChunk.length() + para.length() > MAX_CHUNK_SIZE && currentChunk.length() >= MIN_CHUNK_SIZE) {
                chunks.add(createChunk(sourceId, chunkIndex++, currentChunk.toString()));
                currentChunk = new StringBuilder(getOverlap(currentChunk.toString(), OVERLAP_SIZE));
            }

            // If it's a huge paragraph and the current chunk isn't big enough
            if (currentChunk.length() + para.length() > MAX_CHUNK_SIZE && currentChunk.length() < MIN_CHUNK_SIZE) {
                String[] sentences = para.split("(?<=\\.)\\s+");
                for (String sentence : sentences) {
                    if (currentChunk.length() + sentence.length() > MAX_CHUNK_SIZE && currentChunk.length() > 0) {
                        chunks.add(createChunk(sourceId, chunkIndex++, currentChunk.toString()));
                        currentChunk = new StringBuilder(getOverlap(currentChunk.toString(), OVERLAP_SIZE));
                    }
                    currentChunk.append(sentence).append(" ");
                }
                currentChunk.append("\n\n");
            } else {
                currentChunk.append(para).append("\n\n");
            }
        }

        String remaining = currentChunk.toString().trim();
        if (!remaining.isEmpty()) {
            chunks.add(createChunk(sourceId, chunkIndex++, remaining));
        }

        return chunks;
    }

    private DocumentChunk createChunk(String sourceId, int index, String content) {
        return new DocumentChunk(UUID.randomUUID(), sourceId, index, content.trim());
    }

    private String getOverlap(String text, int targetOverlap) {
        if (text.length() <= targetOverlap) return text;

        int startIndex = text.length() - targetOverlap;

        // Try paragraph break
        int paraBreak = text.indexOf("\n\n", startIndex - 50);
        if (paraBreak != -1 && paraBreak < text.length() - 50) {
            return text.substring(paraBreak).trim() + "\n\n";
        }

        // Try sentence break
        int sentBreak = text.indexOf(". ", startIndex - 20);
        if (sentBreak != -1 && sentBreak < text.length() - 20) {
            return text.substring(sentBreak + 2).trim() + " ";
        }

        // Try word break
        int wordBreak = text.indexOf(" ", startIndex);
        if (wordBreak != -1) {
            return text.substring(wordBreak + 1).trim() + " ";
        }

        return text.substring(startIndex).trim();
    }
}
