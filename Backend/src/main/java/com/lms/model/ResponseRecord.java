package com.lms.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

/**
 * Records a student's answer to a single question.
 *
 * Used by:
 *   - AdaptiveEngineService  (history analysis)
 *   - PredictionEngine       (failure probability)
 *   - UserProfileService     (profile building)
 *   - DecisionFeedbackEngine (impact evaluation)
 *
 * NOTE: PredictionEngine (legacy package) references
 *       com.lms.adaptive.model.ResponseRecord — that class is an alias
 *       of this one (see package note below). Both resolve to identical
 *       fields; Spring Boot uses this canonical version.
 */
@Document(collection = "responses")
public class ResponseRecord {

    @Id
    private String id;

    @Indexed
    private String  userId;
    @Indexed
    private String  questionId;
    private String  concept;
    private int     difficulty;
    private boolean correct;
    private String  studentAnswer;
    @org.springframework.data.mongodb.core.index.Indexed
    private long    timestamp;

    // ── Constructors ──────────────────────────────────────────────────────

    public ResponseRecord() {
        this.timestamp = System.currentTimeMillis();
    }

    public ResponseRecord(String questionId, String concept,
                          int difficulty, boolean correct) {
        this();
        this.questionId = questionId;
        this.concept    = concept;
        this.difficulty = difficulty;
        this.correct    = correct;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────

    public String getId()              { return id; }
    public void   setId(String id)     { this.id = id; }

    public String getQuestionId()                    { return questionId; }
    public void   setQuestionId(String questionId)   { this.questionId = questionId; }

    public String getConcept()               { return concept; }
    public void   setConcept(String concept) { this.concept = concept; }

    public int  getDifficulty()          { return difficulty; }
    public void setDifficulty(int d)     { this.difficulty = d; }

    public boolean isCorrect()              { return correct; }
    public void    setCorrect(boolean c)    { this.correct = c; }

    public String getUserId()                { return userId; }
    public void   setUserId(String userId)   { this.userId = userId; }

    public String getStudentAnswer()                      { return studentAnswer; }
    public void   setStudentAnswer(String studentAnswer)  { this.studentAnswer = studentAnswer; }

    public long getLongTimestamp()           { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
