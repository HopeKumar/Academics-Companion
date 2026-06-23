package com.lms.repository;

import com.lms.model.ResponseRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * MongoDB repository for ResponseRecord documents.
 */
@Repository
public interface ResponseRecordRepository extends MongoRepository<ResponseRecord, String> {

    /**
     * Find all answer history records for a specific user.
     */
    List<ResponseRecord> findByUserId(String userId);

    /**
     * Find user answer history in chronological order.
     */
    List<ResponseRecord> findByUserIdOrderByTimestampAsc(String userId);

    long countByUserId(String userId);
    
    long countByUserIdAndCorrectTrue(String userId);

    Page<ResponseRecord> findByUserIdOrderByTimestampAsc(String userId, Pageable pageable);
    
    Page<ResponseRecord> findByUserId(String userId, Pageable pageable);
}
