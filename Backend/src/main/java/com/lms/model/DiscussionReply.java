package com.lms.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document(collection = "discussion_replies")
public class DiscussionReply {

    @Id
    private String id;
    
    @Indexed
    private String threadId;
    
    private String content;
    private String authorId;
    private int upvotes;
    private boolean acceptedAnswer;
    private String createdAt;

    public DiscussionReply() {
        this.createdAt = Instant.now().toString();
        this.upvotes = 0;
        this.acceptedAnswer = false;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getThreadId() { return threadId; }
    public void setThreadId(String threadId) { this.threadId = threadId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }

    public int getUpvotes() { return upvotes; }
    public void setUpvotes(int upvotes) { this.upvotes = upvotes; }

    public boolean isAcceptedAnswer() { return acceptedAnswer; }
    public void setAcceptedAnswer(boolean acceptedAnswer) { this.acceptedAnswer = acceptedAnswer; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
