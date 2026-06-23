package com.lms.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.CompoundIndex;

import java.time.Instant;

/**
 * Per-student, per-topic mastery record stored in MongoDB.
 *
 * Converted from Vert.x version:
 *   - Removed io.vertx.core.json.JsonObject dependency
 *   - toJson() / fromJson() replaced with Spring Data field mapping
 *   - @Document annotation added for MongoDB collection binding
 *   - All intelligence logic (confidence model, trend engine,
 *     adaptive difficulty, levelDelta) PRESERVED EXACTLY
 *
 * Stored in collection: "topic_mastery"
 * Compound key: { studentId, topic }
 */
@Document(collection = "topic_mastery")
@CompoundIndex(name = "studentId_1_topic_1", def = "{'studentId': 1, 'topic': 1}", unique = true)
public class TopicMastery {

    // Confidence normalisation ceiling: log(101) ≈ 4.615
    private static final int    MAX_ATTEMPTS_NORM  = 100;
    private static final double LOG_NORM           = Math.log(MAX_ATTEMPTS_NORM + 1);

    // Trend engine — accuracy delta threshold
    private static final double TREND_THRESHOLD    = 0.05;

    // Momentum window size
    private static final int    WINDOW_SIZE        = 5;

    // ── Persisted fields ──────────────────────────────────────────────────

    @Id
    private String id;   // MongoDB ObjectId (auto-generated)

    @Indexed
    private String studentId;
    private String subject;
    private String topic;
    private int    totalQuestions;
    private int    incorrectAnswers;
    private double masteryScore;
    private int    correctAnswers;
    private double accuracy;           // 0.0 – 1.0
    private double confidenceScore;    // normalised 0–1 (confidence model)
    private String recentWindow;       // bit-string e.g. "10110"
    private double previousAccuracy;   // trend engine
    private String trend;              // "improving" | "declining" | "stable"
    private String lastUpdated;

    // ── Constructors ──────────────────────────────────────────────────────

    /** Required by Spring Data */
    public TopicMastery() {}

    private TopicMastery(String id, String studentId, String subject, String topic, int totalQuestions,
                         int correctAnswers, int incorrectAnswers, double accuracy, double confidenceScore,
                         double masteryScore, String recentWindow, double previousAccuracy,
                         String trend, String lastUpdated) {
        this.id               = id;
        this.studentId        = studentId;
        this.subject          = subject;
        this.topic            = topic;
        this.totalQuestions   = totalQuestions;
        this.correctAnswers   = correctAnswers;
        this.incorrectAnswers = incorrectAnswers;
        this.accuracy         = accuracy;
        this.confidenceScore  = confidenceScore;
        this.masteryScore     = masteryScore;
        this.recentWindow     = recentWindow;
        this.previousAccuracy = previousAccuracy;
        this.trend            = trend;
        this.lastUpdated      = lastUpdated;
    }

    // ── Factory: brand-new mastery record ────────────────────────────────

    public static TopicMastery create(String studentId, String subject, String topic) {
        return new TopicMastery(null, studentId, subject, topic, 0, 0, 0, 0.0, 0.0, 0.0,
                "", 0.0, "stable", Instant.now().toString());
    }

    // ── Immutable update methods ──────────────────────────────────────────

    public TopicMastery withCorrectAnswer() { return recordAnswer(true); }
    public TopicMastery withWrongAnswer()   { return recordAnswer(false); }

    // ── Adaptive difficulty ───────────────────────────────────────────────

    /**
     * Maps confidence to a difficulty tier.
     *   < 0.40  → EASY
     *   0.40–0.70 → MEDIUM
     *   > 0.70  → HARD
     */
    public DifficultyTier targetDifficulty() {
        if (confidenceScore < 0.40) return DifficultyTier.EASY;
        if (confidenceScore < 0.70) return DifficultyTier.MEDIUM;
        return DifficultyTier.HARD;
    }

    /**
     * Level delta driven by confidence AND trend.
     *   confidence > 0.8 AND improving  → +2
     *   confidence 0.6–0.8              → +1
     *   confidence < 0.4 OR declining   → -1
     *   else                            →  0
     */
    public int getLevelDelta() {
        if (confidenceScore > 0.80 && "improving".equals(trend)) return +2;
        if (confidenceScore >= 0.60)                              return +1;
        if (confidenceScore < 0.40 || "declining".equals(trend)) return -1;
        return 0;
    }

    public enum DifficultyTier { EASY, MEDIUM, HARD }

    // ── Core update logic (PRESERVED from original) ───────────────────────

    private TopicMastery recordAnswer(boolean correct) {
        int    newTotal    = totalQuestions + 1;
        int    newCorrect  = correctAnswers + (correct ? 1 : 0);
        double newAccuracy = (double) newCorrect / newTotal;

        // Confidence model: accuracy * log(attempts+1) / log(MAX+1)
        double rawConf    = newAccuracy * Math.log(newTotal + 1);
        double newConf    = Math.min(1.0, rawConf / LOG_NORM);

        // Trend engine
        String newTrend;
        if      (newAccuracy > previousAccuracy + TREND_THRESHOLD) newTrend = "improving";
        else if (newAccuracy < previousAccuracy - TREND_THRESHOLD) newTrend = "declining";
        else                                                        newTrend = "stable";

        // Sliding recent window
        String newWindow = recentWindow + (correct ? "1" : "0");
        if (newWindow.length() > WINDOW_SIZE) {
            newWindow = newWindow.substring(newWindow.length() - WINDOW_SIZE);
        }

        return new TopicMastery(
                id,
                studentId, subject, topic, newTotal, newCorrect, newTotal - newCorrect,
                newAccuracy, newConf, newConf * 100.0,
                newWindow,
                accuracy,   // current accuracy becomes previousAccuracy
                newTrend,
                Instant.now().toString()
        );
    }

    // ── Getters / Setters (Spring Data requires setters for deserialization) ──

    public String getId()                     { return id; }
    public void   setId(String id)            { this.id = id; }

    public String getStudentId()              { return studentId; }
    public void   setStudentId(String v)      { this.studentId = v; }

    public String getSubject()                { return subject; }
    public void   setSubject(String v)        { this.subject = v; }

    public String getTopic()                  { return topic; }
    public void   setTopic(String v)          { this.topic = v; }

    public int  getTotalQuestions()           { return totalQuestions; }
    public void setTotalQuestions(int v)      { this.totalQuestions = v; }
    
    public int  getIncorrectAnswers()         { return incorrectAnswers; }
    public void setIncorrectAnswers(int v)    { this.incorrectAnswers = v; }
    
    public double getMasteryScore()           { return masteryScore; }
    public void setMasteryScore(double v)     { this.masteryScore = v; }

    public int  getCorrectAnswers()           { return correctAnswers; }
    public void setCorrectAnswers(int v)      { this.correctAnswers = v; }

    public double getAccuracy()               { return accuracy; }
    public void   setAccuracy(double v)       { this.accuracy = v; }

    public double getConfidenceScore()        { return confidenceScore; }
    public void   setConfidenceScore(double v){ this.confidenceScore = v; }

    public String getRecentWindow()           { return recentWindow; }
    public void   setRecentWindow(String v)   { this.recentWindow = v; }

    public double getPreviousAccuracy()       { return previousAccuracy; }
    public void   setPreviousAccuracy(double v){ this.previousAccuracy = v; }

    public String getTrend()                  { return trend; }
    public void   setTrend(String v)          { this.trend = v; }

    public String getLastUpdated()            { return lastUpdated; }
    public void   setLastUpdated(String v)    { this.lastUpdated = v; }
}
