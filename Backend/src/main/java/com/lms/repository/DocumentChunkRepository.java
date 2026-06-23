package com.lms.repository;

import com.lms.model.DocumentChunk;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * MongoDB repository for DocumentChunk documents.
 */
@Repository
public interface DocumentChunkRepository extends MongoRepository<DocumentChunk, String> {

    /**
     * Find all chunks associated with a specific source.
     */
    List<DocumentChunk> findBySourceId(String sourceId);

    /**
     * Find all chunks that belong to any of the specified source IDs.
     */
    List<DocumentChunk> findBySourceIdIn(List<String> sourceIds);

    /**
     * Find all chunks belonging to a specific user.
     */
    List<DocumentChunk> findByUserId(String userId);

    Page<DocumentChunk> findByUserId(String userId, Pageable pageable);

    Page<DocumentChunk> findBySourceId(String sourceId, Pageable pageable);

    Page<DocumentChunk> findBySourceIdAndTextContainingIgnoreCase(String sourceId, String text, Pageable pageable);

    /**
     * Delete all chunks associated with a specific source.
     */
    void deleteBySourceId(String sourceId);
}
