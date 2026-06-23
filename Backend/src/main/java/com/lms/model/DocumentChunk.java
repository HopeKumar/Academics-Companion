package com.lms.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.util.List;

/**
 * Represents a parsed segment of a source document stored with its vector embedding.
 */
@Document(collection = "document_chunks")
public class DocumentChunk {

    @Id
    private String id;

    @Indexed
    private String       sourceId;
    @Indexed
    private String       userId;
    private String       text;
    private List<Double> embedding;
    private int          pageNumber;
    private int          chunkIndex;
    @org.springframework.data.mongodb.core.mapping.Field("created_at")
    private String       createdAt;

    // ── Constructors ──────────────────────────────────────────────────────

    public DocumentChunk() {
        this.createdAt = java.time.Instant.now().toString();
    }

    public DocumentChunk(String sourceId, String userId, String text, List<Double> embedding, int pageNumber) {
        this.sourceId   = sourceId;
        this.userId     = userId;
        this.text       = text;
        this.embedding  = embedding;
        this.pageNumber = pageNumber;
        this.createdAt  = java.time.Instant.now().toString();
    }

    public DocumentChunk(String sourceId, String userId, String text, List<Double> embedding, int pageNumber, int chunkIndex, String createdAt) {
        this.sourceId   = sourceId;
        this.userId     = userId;
        this.text       = text;
        this.embedding  = embedding;
        this.pageNumber = pageNumber;
        this.chunkIndex = chunkIndex;
        this.createdAt  = createdAt != null ? createdAt : java.time.Instant.now().toString();
    }

    // ── Getters / Setters ─────────────────────────────────────────────────

    public String getId()            { return id; }
    public void   setId(String id)   { this.id = id; }

    public String getSourceId()                  { return sourceId; }
    public void   setSourceId(String sourceId)   { this.sourceId = sourceId; }

    public String getUserId()                { return userId; }
    public void   setUserId(String userId)   { this.userId = userId; }

    public String getText()              { return text; }
    public void   setText(String text)   { this.text = text; }

    public List<Double> getEmbedding()                  { return embedding; }
    public void         setEmbedding(List<Double> emb)  { this.embedding = emb; }

    public int  getPageNumber()              { return pageNumber; }
    public void setPageNumber(int pageNumber) { this.pageNumber = pageNumber; }

    public int  getChunkIndex()              { return chunkIndex; }
    public void setChunkIndex(int chunkIndex) { this.chunkIndex = chunkIndex; }

    public String getCreatedAt()             { return createdAt; }
    public void   setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
