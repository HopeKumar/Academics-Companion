package com.lms.model;

/**
 * Response DTO for POST /test/next and POST /test/simulate.
 *
 * Replaces the raw Map<String,Object> returned previously.
 * Structured so clients have a stable, typed contract.
 */
public class TestResponse {

    private Question question;
    private String   reason;

    public TestResponse() {}

    public TestResponse(Question question, String reason) {
        this.question = question;
        this.reason   = reason;
    }

    public Question getQuestion()              { return question; }
    public void     setQuestion(Question q)    { this.question = q; }

    public String getReason()              { return reason; }
    public void   setReason(String reason) { this.reason = reason; }
}
