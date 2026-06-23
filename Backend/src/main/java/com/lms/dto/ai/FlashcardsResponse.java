package com.lms.dto.ai;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.List;

public record FlashcardsResponse(
    @JsonAlias({"flashcards", "cards", "deck"}) List<FlashcardDTO> flashcards
) {}
