package com.lms.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request DTO for POST /test/next and POST /test/simulate.
 *
 * Replaces the inline record NextQuestionRequest in TestController.
 * Validated with @Valid before reaching service layer.
 */
public class TestRequest {

    @NotBlank(message = "userId is required")
    private String userId;

    @Valid
    private List<Question> questions;

    private List<ResponseRecord> history;  // optional — empty list means first question

    public TestRequest() {}

    public String getUserId()                   { return userId; }
    public void   setUserId(String userId)      { this.userId = userId; }

    public List<Question> getQuestions()                  { return questions; }
    public void           setQuestions(List<Question> q)  { this.questions = q; }

    public List<ResponseRecord> getHistory()                      { return history; }
    public void                 setHistory(List<ResponseRecord> h){ this.history = h; }
}
