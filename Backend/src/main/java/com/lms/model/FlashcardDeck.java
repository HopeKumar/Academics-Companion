package com.lms.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import java.time.Instant;

@Document(collection = "flashcard_decks")
public class FlashcardDeck {

    @Id
    private String id;
    @Indexed
    private String userId;
    @Indexed
    private String sourceId; // optional, if generated from a specific source
    private String topic;
    private String title;
    @Indexed
    private String createdAt;
    private String generationSource;

    public FlashcardDeck() {}

    public FlashcardDeck(String userId, String sourceId, String topic, String title) {
        this.userId = userId;
        this.sourceId = sourceId;
        this.topic = topic;
        this.title = title;
        this.createdAt = Instant.now().toString();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getGenerationSource() { return generationSource; }
    public void setGenerationSource(String generationSource) { this.generationSource = generationSource; }
}
