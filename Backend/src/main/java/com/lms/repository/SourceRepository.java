package com.lms.repository;

import com.lms.model.Source;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * MongoDB repository for Source documents.
 */
@Repository
public interface SourceRepository extends MongoRepository<Source, String> {

    /**
     * Find all sources owned by a user.
     */
    List<Source> findByUserId(String userId);

    Page<Source> findByUserId(String userId, Pageable pageable);
}
