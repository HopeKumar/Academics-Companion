package com.lms.dto.ai;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.List;

public record QuizQuestionDTO(
    String question,
    @JsonAlias({"options", "choices", "answers"}) List<String> options,
    @JsonAlias({"correctAnswer", "correct_answer", "correct"}) String correctAnswer,
    @JsonAlias({"explanation", "reason"}) String explanation
) {}
