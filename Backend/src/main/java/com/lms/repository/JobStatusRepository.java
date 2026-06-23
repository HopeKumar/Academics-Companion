package com.lms.repository;

import com.lms.model.JobStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobStatusRepository extends MongoRepository<JobStatus, String> {
    List<JobStatus> findBySourceId(String sourceId);
    Optional<JobStatus> findBySourceIdAndType(String sourceId, String type);
}
