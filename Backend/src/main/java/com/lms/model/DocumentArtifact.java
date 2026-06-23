package com.lms.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import java.time.LocalDateTime;

@Document(collection = "document_artifacts")
public class DocumentArtifact {
    @Id
    private String id;

    @Field("document_id")
    private String documentId;

    private ArtifactType type;

    @Field("storage_key")
    private String storageKey; 

    private String status; 

    @Field("created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    // Getters and Setters

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public ArtifactType getType() { return type; }
    public void setType(ArtifactType type) { this.type = type; }

    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
