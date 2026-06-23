package com.lms.repository;

import com.lms.model.SlideExport;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SlideExportRepository extends MongoRepository<SlideExport, String> {
    List<SlideExport> findByUserIdOrderByGeneratedAtDesc(String userId);
    Optional<SlideExport> findByUserIdAndSourceId(String userId, String sourceId);
    void deleteBySourceId(String sourceId);
}
