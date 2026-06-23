package com.lms.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.Instant;

/**
 * Represents a tutoring chat session owned by a user.
 */
@Document(collection = "chat_sessions")
public class ChatSession {

    @Id
    private String id;

    @Indexed
    private String userId;
    private String title;
    @Indexed
    private String createdAt;
    private String updatedAt;

    // ── Constructors ──────────────────────────────────────────────────────

    public ChatSession() {
        this.createdAt = Instant.now().toString();
        this.updatedAt = Instant.now().toString();
    }

    public ChatSession(String userId, String title) {
        this();
        this.userId = userId;
        this.title  = title;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────

    public String getId()            { return id; }
    public void   setId(String id)   { this.id = id; }

    public String getUserId()                { return userId; }
    public void   setUserId(String userId)   { this.userId = userId; }

    public String getTitle()              { return title; }
    public void   setTitle(String title)   { this.title = title; }

    public String getCreatedAt()                 { return createdAt; }
    public void   setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt()                 { return updatedAt; }
    public void   setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
