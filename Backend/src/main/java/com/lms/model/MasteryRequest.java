package com.lms.model;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for POST /test/mastery.
 *
 * Extracted from the inline record in TestController into a proper model class.
 */
public class MasteryRequest {

    @NotBlank(message = "studentId is required")
    private String studentId;

    @NotBlank(message = "topic is required")
    private String topic;

    private boolean correct;

    public MasteryRequest() {}

    public String  getStudentId()                   { return studentId; }
    public void    setStudentId(String studentId)   { this.studentId = studentId; }

    public String  getTopic()              { return topic; }
    public void    setTopic(String topic)  { this.topic = topic; }

    public boolean isCorrect()               { return correct; }
    public void    setCorrect(boolean c)     { this.correct = c; }
}
