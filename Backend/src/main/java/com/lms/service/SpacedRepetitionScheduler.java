package com.lms.service;

import com.lms.model.Flashcard;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Pure SM-2 spaced repetition algorithm — extracted from FlashcardService
 * for testability and separation of concerns.
 *
 * SM-2 algorithm (Supermemo 2):
 *   - quality: 0 = complete blackout, 5 = perfect recall
 *   - If quality < 3: reset repetitions to 0, interval to 1 day
 *   - If quality >= 3:
 *       - rep 0 → interval = 1 day
 *       - rep 1 → interval = 6 days
 *       - rep n → interval = previousInterval * easeFactor
 *   - easeFactor adjusted: EF = EF + (0.1 - (5-q) * (0.08 + (5-q) * 0.02))
 *   - easeFactor minimum: 1.3
 *
 * This class is a pure function — it receives a Flashcard, returns a MODIFIED COPY.
 * It does NOT save to the database.
 */
@Component
public class SpacedRepetitionScheduler {

    private static final float MIN_EASE_FACTOR = 1.3f;

    /**
     * Compute the next review schedule for a flashcard given a quality rating.
     *
     * @param card    The flashcard to schedule.
     * @param quality The quality rating: 0 (complete blackout) to 5 (perfect recall).
     * @return A new Flashcard instance with updated interval, repetitions, easeFactor,
     *         and nextReviewDate. The original is not modified.
     */
    public Flashcard scheduleNext(Flashcard card, int quality) {
        if (quality < 0 || quality > 5) {
            throw new IllegalArgumentException("Quality must be between 0 and 5, got: " + quality);
        }

        int   newRepetitions;
        int   newInterval;
        float newEaseFactor;

        if (quality < 3) {
            // Failed — reset to beginning
            newRepetitions = 0;
            newInterval    = 1;
            newEaseFactor  = adjustEaseFactor(card.getEaseFactor(), quality);
        } else {
            // Passed — advance the schedule
            newRepetitions = card.getRepetitions() + 1;
            newInterval    = computeInterval(card.getRepetitions(), card.getInterval(), card.getEaseFactor());
            newEaseFactor  = adjustEaseFactor(card.getEaseFactor(), quality);
        }

        String nextReviewDate = Instant.now()
                .plus(newInterval, ChronoUnit.DAYS)
                .toString();

        // Apply changes to a copy
        card.setRepetitions(newRepetitions);
        card.setInterval(newInterval);
        card.setEaseFactor(newEaseFactor);
        card.setNextReviewDate(nextReviewDate);
        return card;
    }

    /**
     * Check whether a card is due for review (nextReviewDate <= now).
     */
    public boolean isDue(Flashcard card) {
        try {
            Instant nextReview = Instant.parse(card.getNextReviewDate());
            return !nextReview.isAfter(Instant.now());
        } catch (Exception e) {
            return true; // If unparseable, treat as due
        }
    }

    // ── SM-2 internals ────────────────────────────────────────────────────

    private int computeInterval(int repetitions, int currentInterval, float easeFactor) {
        return switch (repetitions) {
            case 0  -> 1;
            case 1  -> 6;
            default -> Math.round(currentInterval * easeFactor);
        };
    }

    private float adjustEaseFactor(float currentEF, int quality) {
        float delta = 0.1f - (5 - quality) * (0.08f + (5 - quality) * 0.02f);
        return Math.max(MIN_EASE_FACTOR, currentEF + delta);
    }
}
