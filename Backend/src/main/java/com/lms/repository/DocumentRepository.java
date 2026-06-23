package com.lms.repository;

import com.lms.model.Document;
import com.lms.model.DocumentStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends MongoRepository<Document, String> {
    Optional<Document> findByFileHashSha256(String fileHashSha256);
    List<Document> findByStatusAndCreatedAtBefore(DocumentStatus status, LocalDateTime threshold);
    List<Document> findByStatus(DocumentStatus status);
}
