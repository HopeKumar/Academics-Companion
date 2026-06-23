package com.lms.dto.ai;

import com.fasterxml.jackson.annotation.JsonAlias;

public record FlashcardDTO(
    @JsonAlias({"topic", "front", "question"}) String topic,
    @JsonAlias({"explanation", "back", "answer"}) String explanation
) {}
