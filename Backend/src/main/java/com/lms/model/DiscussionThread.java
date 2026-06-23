package com.lms.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document(collection = "discussion_threads")
public class DiscussionThread {

    @Id
    private String id;
    private String title;
    private String content;
    private String sourceId;
    @org.springframework.data.mongodb.core.index.Indexed
    private String authorId;
    private String aiSummary;
    private int upvotes;
    private String createdAt;
    private String updatedAt;

    @org.springframework.data.annotation.Transient
    private java.util.List<DiscussionReply> threadMessages;

    public DiscussionThread() {
        this.createdAt = Instant.now().toString();
        this.updatedAt = Instant.now().toString();
        this.upvotes = 0;
    }

    public java.util.List<DiscussionReply> getThreadMessages() { return threadMessages; }
    public void setThreadMessages(java.util.List<DiscussionReply> threadMessages) { this.threadMessages = threadMessages; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }

    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }

    public String getAiSummary() { return aiSummary; }
    public void setAiSummary(String aiSummary) { this.aiSummary = aiSummary; }

    public int getUpvotes() { return upvotes; }
    public void setUpvotes(int upvotes) { this.upvotes = upvotes; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
