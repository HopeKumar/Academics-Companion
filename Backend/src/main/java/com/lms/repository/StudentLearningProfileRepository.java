package com.lms.repository;

import com.lms.model.StudentLearningProfile;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StudentLearningProfileRepository extends MongoRepository<StudentLearningProfile, String> {
    Optional<StudentLearningProfile> findByUserId(String userId);
}
