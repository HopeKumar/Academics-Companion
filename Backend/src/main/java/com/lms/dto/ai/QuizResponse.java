package com.lms.dto.ai;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.List;

public record QuizResponse(
    @JsonAlias({"questions", "quiz", "items"}) List<QuizQuestionDTO> questions
) {}
