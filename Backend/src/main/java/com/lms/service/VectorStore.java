package com.lms.service;

import com.lms.model.DocumentChunk;
import java.util.List;

public interface VectorStore {
    void upsertChunks(List<DocumentChunk> chunks);
    List<DocumentChunk> search(List<Double> queryVector, List<String> sourceIds, String userId, int topK);
    void deleteBySourceId(String sourceId);
}
