package com.lms.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A notification delivered to a specific user.
 *
 * Types:
 *   UPLOAD_COMPLETE   — document ingestion finished
 *   UPLOAD_FAILED     — document ingestion failed
 *   STUDY_REMINDER    — scheduled study prompt
 *   MILESTONE         — achievement reached (e.g., 7-day streak)
 *   AI_ALERT          — AI detected learning risk
 *   RECOMMENDATION    — new study recommendation available
 */
@Document(collection = "notifications")
public class Notification {

    public enum Type {
        UPLOAD_COMPLETE, UPLOAD_FAILED, STUDY_REMINDER,
        MILESTONE, AI_ALERT, RECOMMENDATION
    }

    @Id
    private String id;

    @Indexed
    private String userId;

    private Type    type;
    private String  title;
    private String  message;
    private boolean read;

    @Indexed
    private String  createdAt;
    @Indexed
    private long    createdAtMs;

    // Reference to the resource that triggered this notification (optional)
    private String  resourceId;
    private String  resourceType;

    public Notification() {}

    public Notification(String userId, Type type, String title, String message) {
        this.userId      = userId;
        this.type        = type;
        this.title       = title;
        this.message     = message;
        this.read        = false;
        this.createdAt   = Instant.now().toString();
        this.createdAtMs = System.currentTimeMillis();
    }

    // ── Getters / Setters ─────────────────────────────────────────────────

    public String  getId()              { return id; }
    public void    setId(String v)      { this.id = v; }

    public String  getUserId()          { return userId; }
    public void    setUserId(String v)  { this.userId = v; }

    public Type    getType()            { return type; }
    public void    setType(Type v)      { this.type = v; }

    public String  getTitle()           { return title; }
    public void    setTitle(String v)   { this.title = v; }

    public String  getMessage()         { return message; }
    public void    setMessage(String v) { this.message = v; }

    public boolean isRead()             { return read; }
    public void    setRead(boolean v)   { this.read = v; }

    public String  getCreatedAt()       { return createdAt; }
    public void    setCreatedAt(String v){ this.createdAt = v; }

    public long    getCreatedAtMs()     { return createdAtMs; }
    public void    setCreatedAtMs(long v){ this.createdAtMs = v; }

    public String  getResourceId()      { return resourceId; }
    public void    setResourceId(String v){ this.resourceId = v; }

    public String  getResourceType()    { return resourceType; }
    public void    setResourceType(String v){ this.resourceType = v; }
}
