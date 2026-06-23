package com.lms.repository;

import com.lms.model.DocumentArtifact;
import com.lms.model.Document;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentArtifactRepository extends MongoRepository<DocumentArtifact, String> {
    List<DocumentArtifact> findByDocumentId(String documentId);
}
