package com.lms.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import java.time.Instant;

@Document(collection = "flashcards")
public class Flashcard {

    @Id
    private String id;
    @Indexed
    private String deckId;
    private String front;
    private String back;
    private String topic;
    
    // Spaced repetition fields
    private int interval; // days
    private float easeFactor;
    private int repetitions;
    private String nextReviewDate;

    public Flashcard() {}

    public Flashcard(String deckId, String front, String back, String topic) {
        this.deckId = deckId;
        this.front = front;
        this.back = back;
        this.topic = topic;
        this.interval = 0;
        this.easeFactor = 2.5f;
        this.repetitions = 0;
        this.nextReviewDate = Instant.now().toString();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDeckId() { return deckId; }
    public void setDeckId(String deckId) { this.deckId = deckId; }
    public String getFront() { return front; }
    public void setFront(String front) { this.front = front; }
    public String getBack() { return back; }
    public void setBack(String back) { this.back = back; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public int getInterval() { return interval; }
    public void setInterval(int interval) { this.interval = interval; }
    public float getEaseFactor() { return easeFactor; }
    public void setEaseFactor(float easeFactor) { this.easeFactor = easeFactor; }
    public int getRepetitions() { return repetitions; }
    public void setRepetitions(int repetitions) { this.repetitions = repetitions; }
    public String getNextReviewDate() { return nextReviewDate; }
    public void setNextReviewDate(String nextReviewDate) { this.nextReviewDate = nextReviewDate; }
}
