package com.lms.unit;

import com.lms.TestFixtures;
import com.lms.model.Flashcard;
import com.lms.model.FlashcardDeck;
import com.lms.repository.FlashcardDeckRepository;
import com.lms.repository.FlashcardRepository;
import com.lms.service.FlashcardService;
import com.lms.service.ContextBuilder;
import com.lms.service.HybridRetrievalService;
import com.lms.service.SpacedRepetitionScheduler;
import com.lms.service.ai.AIOrchestratorService;
import com.lms.service.JobStatusService;
import com.lms.repository.SourceRepository;
import com.lms.service.ai.provider.PromptManager;
import com.lms.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FlashcardService — SM-2 spaced repetition algorithm.
 * All dependencies are mocked; no Spring context or database needed.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FlashcardService — SM-2 Spaced Repetition")
class FlashcardServiceTest {

    @Mock private FlashcardRepository     flashcardRepository;
    @Mock private FlashcardDeckRepository deckRepository;
    @Mock private HybridRetrievalService  retrievalService;
    @Mock private ContextBuilder          contextBuilder;
    @Mock private AIOrchestratorService   aiOrchestratorService;
    @Mock private JobStatusService        jobStatusService;
    @Mock private SourceRepository        sourceRepository;
    @Mock private PromptManager           promptManager;

    private SpacedRepetitionScheduler sm2;
    private FlashcardService          service;

    @BeforeEach
    void setUp() {
        sm2 = new SpacedRepetitionScheduler();
        service = new FlashcardService(flashcardRepository, deckRepository,
                retrievalService, contextBuilder, aiOrchestratorService, sm2, jobStatusService,
                sourceRepository, promptManager);
    }

    // ── SM-2 Algorithm Tests ──────────────────────────────────────────────

    @Test
    @DisplayName("Quality 5 (perfect) — first review sets interval to 1 day")
    void quality5FirstReview() {
        Flashcard card = TestFixtures.flashcard("deck-1");
        card.setRepetitions(0);
        card.setInterval(0);
        card.setEaseFactor(2.5f);

        FlashcardDeck mockDeck = TestFixtures.flashcardDeck("user-1");
        mockDeck.setId("deck-1");
        when(deckRepository.findById("deck-1")).thenReturn(Optional.of(mockDeck));

        when(flashcardRepository.findById("card-1")).thenReturn(Optional.of(card));
        when(flashcardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.reviewFlashcard("card-1", 5);

        verify(flashcardRepository).save(argThat(saved -> {
            assertThat(saved.getInterval()).isEqualTo(1);
            assertThat(saved.getRepetitions()).isEqualTo(1);
            assertThat(saved.getEaseFactor()).isGreaterThan(2.5f);
            return true;
        }));
    }

    @Test
    @DisplayName("Quality 4 — second review sets interval to 6 days")
    void quality4SecondReview() {
        Flashcard card = TestFixtures.flashcard("deck-1");
        card.setRepetitions(1);
        card.setInterval(1);
        card.setEaseFactor(2.5f);

        FlashcardDeck mockDeck = TestFixtures.flashcardDeck("user-1");
        mockDeck.setId("deck-1");
        when(deckRepository.findById("deck-1")).thenReturn(Optional.of(mockDeck));

        when(flashcardRepository.findById("card-1")).thenReturn(Optional.of(card));
        when(flashcardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.reviewFlashcard("card-1", 4);

        verify(flashcardRepository).save(argThat(saved -> {
            assertThat(saved.getInterval()).isEqualTo(6);
            assertThat(saved.getRepetitions()).isEqualTo(2);
            return true;
        }));
    }

    @Test
    @DisplayName("Quality 2 (fail) — resets repetitions to 0 and interval to 1")
    void qualityBelowThreeResetsCard() {
        Flashcard card = TestFixtures.flashcard("deck-1");
        card.setRepetitions(5);
        card.setInterval(21);
        card.setEaseFactor(2.5f);

        FlashcardDeck mockDeck = TestFixtures.flashcardDeck("user-1");
        mockDeck.setId("deck-1");
        when(deckRepository.findById("deck-1")).thenReturn(Optional.of(mockDeck));

        when(flashcardRepository.findById("card-1")).thenReturn(Optional.of(card));
        when(flashcardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.reviewFlashcard("card-1", 2);

        verify(flashcardRepository).save(argThat(saved -> {
            assertThat(saved.getRepetitions()).isEqualTo(0);
            assertThat(saved.getInterval()).isEqualTo(1);
            return true;
        }));
    }

    @Test
    @DisplayName("Quality 0 (blackout) — ease factor drops but never below 1.3")
    void easFactorFloorIsOnePointThree() {
        Flashcard card = TestFixtures.flashcard("deck-1");
        card.setRepetitions(0);
        card.setInterval(0);
        card.setEaseFactor(1.3f);

        FlashcardDeck mockDeck = TestFixtures.flashcardDeck("user-1");
        mockDeck.setId("deck-1");
        when(deckRepository.findById("deck-1")).thenReturn(Optional.of(mockDeck));

        when(flashcardRepository.findById("card-1")).thenReturn(Optional.of(card));
        when(flashcardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.reviewFlashcard("card-1", 0);

        verify(flashcardRepository).save(argThat(saved -> {
            assertThat(saved.getEaseFactor()).isGreaterThanOrEqualTo(1.3f);
            return true;
        }));
    }

    @Test
    @DisplayName("nextReviewDate is set in the future after quality >= 3")
    void nextReviewDateIsInFuture() {
        Flashcard card = TestFixtures.flashcard("deck-1");
        card.setRepetitions(0);

        FlashcardDeck mockDeck = TestFixtures.flashcardDeck("user-1");
        mockDeck.setId("deck-1");
        when(deckRepository.findById("deck-1")).thenReturn(Optional.of(mockDeck));

        when(flashcardRepository.findById("card-1")).thenReturn(Optional.of(card));
        when(flashcardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.reviewFlashcard("card-1", 4);

        verify(flashcardRepository).save(argThat(saved -> {
            Instant nextReview = Instant.parse(saved.getNextReviewDate());
            assertThat(nextReview).isAfter(Instant.now());
            return true;
        }));
    }

    @Test
    @DisplayName("reviewFlashcard throws when card not found")
    void throwsWhenCardNotFound() {
        when(flashcardRepository.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.reviewFlashcard("missing", 5))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Flashcard not found");
    }

    @Test
    @DisplayName("getDecksForUser delegates to repository")
    void getDecksForUserDelegatesToRepository() {
        List<FlashcardDeck> decks = List.of(TestFixtures.flashcardDeck("user-1"));
        when(deckRepository.findByUserId("user-1")).thenReturn(decks);

        List<FlashcardDeck> result = service.getDecksForUser("user-1");

        assertThat(result).hasSize(1);
        verify(deckRepository).findByUserId("user-1");
    }
}
