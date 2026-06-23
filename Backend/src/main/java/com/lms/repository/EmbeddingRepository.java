package com.lms.repository;

import com.lms.model.EmbeddingEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmbeddingRepository extends MongoRepository<EmbeddingEntity, String> {
    List<EmbeddingEntity> findBySourceId(String sourceId);
    void deleteBySourceId(String sourceId);
}
