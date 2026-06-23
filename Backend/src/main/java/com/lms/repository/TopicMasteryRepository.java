package com.lms.repository;

import com.lms.model.TopicMastery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MongoDB repository for topic mastery records.
 *
 * Replaces the Vert.x-based TopicMasteryRepository that used
 * MongoClient callbacks. Spring Data generates all implementations.
 *
 * Indexes to create in MongoDB (run once):
 *   db.topic_mastery.createIndex({ studentId: 1, topic: 1 }, { unique: true })
 *   db.topic_mastery.createIndex({ studentId: 1 })
 */
@Repository
public interface TopicMasteryRepository extends MongoRepository<TopicMastery, String> {

    /**
     * Find a mastery record by student + topic.
     * Returns Optional.empty() if not yet created.
     */
    Optional<TopicMastery> findByStudentIdAndTopic(String studentId, String topic);

    /**
     * All mastery records for one student — used by RecommendationService.
     */
    List<TopicMastery> findByStudentId(String studentId);

    Page<TopicMastery> findByStudentId(String studentId, Pageable pageable);

    List<TopicMastery> findBySubject(String subject);
    
    List<TopicMastery> findByStudentIdAndMasteryScoreLessThanEqual(String studentId, double score);
    
    List<TopicMastery> findByStudentIdAndMasteryScoreGreaterThanEqual(String studentId, double score);
}
