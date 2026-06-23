package com.lms.repository;

import com.lms.model.KnowledgeGraph;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface KnowledgeGraphRepository extends MongoRepository<KnowledgeGraph, String> {
    Optional<KnowledgeGraph> findByUserIdAndSourceId(String userId, String sourceId);
    Optional<KnowledgeGraph> findBySourceId(String sourceId);
    void deleteBySourceId(String sourceId);
}
