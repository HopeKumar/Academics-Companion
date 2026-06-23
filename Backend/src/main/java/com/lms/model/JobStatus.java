package com.lms.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "job_status")
public class JobStatus {

    @Id
    private String id;
    private String sourceId;
    private String type; // SUMMARY, PODCAST, FLASHCARDS, QUIZ, MINDMAP
    private String status; // PENDING, PROCESSING, COMPLETED, FAILED
    private String message;
    private Instant createdAt;
    private Instant updatedAt;

    public JobStatus() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public JobStatus(String sourceId, String type, String status) {
        this();
        this.sourceId = sourceId;
        this.type = type;
        this.status = status;
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
