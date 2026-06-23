package com.lms.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;

@Document(collection = "question_bank")
public class QuestionBank {

    @Id
    private String id;
    
    @org.springframework.data.mongodb.core.index.Indexed
    private String subject;
    private String unit;
    private String difficulty; // EASY, MEDIUM, HARD
    private int year;
    private String question;
    private String answer;
    @org.springframework.data.mongodb.core.index.Indexed
    private List<String> tags;

    public QuestionBank() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
}
