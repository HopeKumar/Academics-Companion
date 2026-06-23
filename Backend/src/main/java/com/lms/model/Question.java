package com.lms.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Represents a quiz question stored in MongoDB (collection: "questions").
 *
 * Used by AdaptiveEngineService for scoring and selection.
 * Also accepted as a plain POJO in request bodies (TestController).
 */
@Document(collection = "questions")
public class Question {

    @Id
    private String id;

    private String text;
    private String concept;
    @org.springframework.data.mongodb.core.index.Indexed
    private int    difficulty;   // 1–4 (maps to AdaptiveEngineService levels)
    private String correctAnswer;
    @org.springframework.data.mongodb.core.index.Indexed
    private String topic;
    private String type; // MCQ, TRUE_FALSE, FILL_IN_THE_BLANK, SCENARIO
    
    @org.springframework.data.mongodb.core.index.Indexed
    private String sourceId;
    
    private java.util.List<String> options;
    private Integer correctOptionIndex;
    private String generationSource;
    private String explanation;

    // ── Constructors ──────────────────────────────────────────────────────

    public Question() {}

    public Question(String id, String text, String concept,
                    int difficulty, String correctAnswer) {
        this.id            = id;
        this.text          = text;
        this.concept       = concept;
        this.difficulty    = difficulty;
        this.correctAnswer = correctAnswer;
        this.topic         = concept;  // default topic = concept
        this.type          = "MCQ";    // default type
    }

    // ── Getters / Setters ─────────────────────────────────────────────────

    public String getId()            { return id; }
    public void   setId(String id)   { this.id = id; }

    public String getText()              { return text; }
    public void   setText(String text)   { this.text = text; }

    public String getConcept()               { return concept; }
    public void   setConcept(String concept) { this.concept = concept; }

    public int  getDifficulty()              { return difficulty; }
    public void setDifficulty(int d)         { this.difficulty = d; }

    public String getCorrectAnswer()                      { return correctAnswer; }
    public void   setCorrectAnswer(String correctAnswer)  { this.correctAnswer = correctAnswer; }

    public String getTopic()              { return topic != null ? topic : concept; }
    public void   setTopic(String topic)  { this.topic = topic; }

    public String getType()              { return type; }
    public void   setType(String type)   { this.type = type; }

    public java.util.List<String> getOptions() { return options; }
    public void setOptions(java.util.List<String> options) { this.options = options; }

    public Integer getCorrectOptionIndex() { return correctOptionIndex; }
    public void setCorrectOptionIndex(Integer correctOptionIndex) { this.correctOptionIndex = correctOptionIndex; }

    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }

    public String getGenerationSource() { return generationSource; }
    public void setGenerationSource(String generationSource) { this.generationSource = generationSource; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
}
