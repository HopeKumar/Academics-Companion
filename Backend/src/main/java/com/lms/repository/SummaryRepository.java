package com.lms.repository;

import com.lms.model.Summary;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SummaryRepository extends MongoRepository<Summary, String> {
    Optional<Summary> findBySourceId(String sourceId);
    void deleteBySourceId(String sourceId);
}
