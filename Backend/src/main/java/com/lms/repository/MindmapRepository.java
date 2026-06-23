package com.lms.repository;

import com.lms.model.MindMap;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MindmapRepository extends MongoRepository<MindMap, String> {
    List<MindMap> findByUserIdOrderByGeneratedAtDesc(String userId);
    Optional<MindMap> findByUserIdAndSourceId(String userId, String sourceId);
    void deleteBySourceId(String sourceId);
}
