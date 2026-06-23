package com.lms.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a chat message within a tutoring session stored in MongoDB.
 */
@Document(collection = "chat_messages")
@org.springframework.data.mongodb.core.index.CompoundIndex(name = "session_time_idx", def = "{'sessionId': 1, 'timestamp': 1}")
public class ChatMessage {

    @Id
    private String id;

    @Indexed
    private String         sessionId;
    private String         sender; // USER, AI
    private String         content;
    private List<Citation> citations = new ArrayList<>();
    private long           timestamp;

    // ── Constructors ──────────────────────────────────────────────────────

    public ChatMessage() {
        this.timestamp = System.currentTimeMillis();
    }

    public ChatMessage(String sessionId, String sender, String content) {
        this();
        this.sessionId = sessionId;
        this.sender    = sender;
        this.content   = content;
    }

    public ChatMessage(String sessionId, String sender, String content, List<Citation> citations) {
        this(sessionId, sender, content);
        if (citations != null) {
            this.citations = citations;
        }
    }

    // ── Getters / Setters ─────────────────────────────────────────────────

    public String getId()            { return id; }
    public void   setId(String id)   { this.id = id; }

    public String getSessionId()                    { return sessionId; }
    public void   setSessionId(String sessionId)   { this.sessionId = sessionId; }

    public String getSender()                { return sender; }
    public void   setSender(String sender)   { this.sender = sender; }

    public String getContent()                 { return content; }
    public void   setContent(String content) { this.content = content; }

    public List<Citation> getCitations()                     { return citations; }
    public void           setCitations(List<Citation> cits)  { this.citations = cits; }

    public long getTimestamp()                 { return timestamp; }
    public void setTimestamp(long timestamp)   { this.timestamp = timestamp; }
}
