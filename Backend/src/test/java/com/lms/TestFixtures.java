package com.lms;

import com.lms.model.*;
import com.lms.service.LlmProvider;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Shared test data factory.
 *
 * Call these static factory methods from any test class to get consistent,
 * pre-built model instances. Avoids copy-paste across test files and ensures
 * test data always matches the model constructors.
 */
public final class TestFixtures {

    private TestFixtures() {}

    // ── Users ─────────────────────────────────────────────────────────────

    public static User studentUser() {
        User user = new User("testStudent", "$2a$10$hashedPassword", Role.STUDENT);
        user.setId("user-student-001");
        return user;
    }

    public static User adminUser() {
        User user = new User("testAdmin", "$2a$10$hashedPassword", Role.ADMIN);
        user.setId("user-admin-001");
        return user;
    }

    // ── Questions ─────────────────────────────────────────────────────────

    public static Question question(String topic, int level) {
        Question q = new Question();
        q.setId("q-" + topic + "-" + level);
        q.setText("What is " + topic + "?");
        q.setCorrectAnswer("The answer to " + topic);
        q.setDifficulty(level);
        q.setConcept(topic);
        return q;
    }

    // ── ResponseRecords ───────────────────────────────────────────────────

    public static ResponseRecord correctResponse(String userId, String topic) {
        ResponseRecord r = new ResponseRecord();
        r.setId("rr-" + System.nanoTime());
        r.setUserId(userId);
        r.setConcept(topic);
        r.setCorrect(true);
        r.setTimestamp(System.currentTimeMillis());
        return r;
    }

    public static ResponseRecord wrongResponse(String userId, String topic) {
        ResponseRecord r = new ResponseRecord();
        r.setId("rr-" + System.nanoTime());
        r.setUserId(userId);
        r.setConcept(topic);
        r.setCorrect(false);
        r.setTimestamp(System.currentTimeMillis());
        return r;
    }

    // ── TopicMastery ──────────────────────────────────────────────────────

    public static TopicMastery freshMastery(String userId, String topic) {
        return TopicMastery.create(userId, "general", topic);
    }

    public static TopicMastery masteredTopic(String userId, String topic) {
        // Simulate 50 correct answers → high confidence (> 0.80)
        TopicMastery m = TopicMastery.create(userId, "general", topic);
        for (int i = 0; i < 50; i++) {
            m = m.withCorrectAnswer();
        }
        return m;
    }

    public static TopicMastery weakTopic(String userId, String topic) {
        // 3 wrong out of 4 → low confidence
        TopicMastery m = TopicMastery.create(userId, "general", topic);
        m = m.withWrongAnswer();
        m = m.withWrongAnswer();
        m = m.withWrongAnswer();
        m = m.withCorrectAnswer();
        return m;
    }

    // ── DocumentChunks ────────────────────────────────────────────────────

    public static DocumentChunk chunk(String sourceId, String userId, String text) {
        return new DocumentChunk(sourceId, userId, text, List.of(0.1, 0.2, 0.3, 0.4), 1);
    }

    // ── Flashcards ────────────────────────────────────────────────────────

    public static Flashcard flashcard(String deckId) {
        return new Flashcard(deckId, "What is Java?", "A compiled, object-oriented language.", "Java");
    }

    public static FlashcardDeck flashcardDeck(String userId) {
        return new FlashcardDeck(userId, "src-001", "Java", "Java Flashcards");
    }

    // ── Chat ──────────────────────────────────────────────────────────────

    public static ChatSession chatSession(String userId) {
        return new ChatSession(userId, "Test Session");
    }

    public static ChatMessage userMessage(String sessionId, String content) {
        return new ChatMessage(sessionId, "USER", content);
    }

    public static ChatMessage aiMessage(String sessionId, String content) {
        return new ChatMessage(sessionId, "AI", content, Collections.emptyList());
    }

    // ── Embeddings ────────────────────────────────────────────────────────

    /** Returns a fixed 4-dimensional unit vector for testing cosine similarity. */
    public static List<Double> testEmbedding() {
        return List.of(0.5, 0.5, 0.5, 0.5);
    }

    public static List<Double> orthogonalEmbedding() {
        return List.of(-0.5, 0.5, -0.5, 0.5);
    }
}
