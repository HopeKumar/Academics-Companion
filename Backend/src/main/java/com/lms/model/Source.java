package com.lms.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a study source document (PDF, DOCX, TXT, URL) uploaded/ingested by a user.
 */
@Document(collection = "sources")
public class Source {

    @Id
    private String id;

    private String              userId;
    private String              name;
    private String              type; // PDF, DOCX, TXT, URL
    private String              url;
    private long                fileSize;
    @Indexed
    private String              createdAt;
    private String              extractedText;
    private Map<String, Object> metadata = new HashMap<>();

    // ── Constructors ──────────────────────────────────────────────────────

    public Source() {
        this.createdAt = Instant.now().toString();
    }

    public Source(String userId, String name, String type, long fileSize) {
        this();
        this.userId   = userId;
        this.name     = name;
        this.type     = type;
        this.fileSize = fileSize;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────

    public String getId()            { return id; }
    public void   setId(String id)   { this.id = id; }

    public String getUserId()                { return userId; }
    public void   setUserId(String userId)   { this.userId = userId; }

    public String getName()              { return name; }
    public void   setName(String name)   { this.name = name; }

    public String getType()              { return type; }
    public void   setType(String type)   { this.type = type; }

    public String getUrl()             { return url; }
    public void   setUrl(String url)   { this.url = url; }

    public long getFileSize()                  { return fileSize; }
    public void setFileSize(long fileSize)     { this.fileSize = fileSize; }

    public String getCreatedAt()                 { return createdAt; }
    public void   setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getExtractedText() { return extractedText; }
    public void setExtractedText(String extractedText) { this.extractedText = extractedText; }

    public Map<String, Object> getMetadata()                     { return metadata; }
    public void                setMetadata(Map<String, Object> m) { this.metadata = m; }
}
