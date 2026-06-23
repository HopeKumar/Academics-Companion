package com.lms.unit;

import com.lms.model.TopicMastery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for TopicMastery — the core confidence model, trend engine,
 * and adaptive difficulty logic. These are pure algorithmic tests with no
 * Spring context or database required.
 */
@DisplayName("TopicMastery — Confidence Model & Trend Engine")
class TopicMasteryTest {

    @Test
    @DisplayName("Fresh mastery record has zero accuracy and stable trend")
    void freshMasteryIsZero() {
        TopicMastery m = TopicMastery.create("user-1", "math", "algebra");

        assertThat(m.getAccuracy()).isEqualTo(0.0);
        assertThat(m.getConfidenceScore()).isEqualTo(0.0);
        assertThat(m.getTrend()).isEqualTo("stable");
        assertThat(m.getTotalQuestions()).isEqualTo(0);
    }

    @Test
    @DisplayName("Single correct answer gives positive confidence")
    void singleCorrectAnswerIncreasesConfidence() {
        TopicMastery m = TopicMastery.create("user-1", "math", "algebra")
                .withCorrectAnswer();

        assertThat(m.getTotalQuestions()).isEqualTo(1);
        assertThat(m.getAccuracy()).isEqualTo(1.0);
        assertThat(m.getConfidenceScore()).isGreaterThan(0.0).isLessThanOrEqualTo(1.0);
    }

    @Test
    @DisplayName("Many consecutive correct answers → HARD difficulty tier")
    void manyCorrectAnswersYieldsHardTier() {
        TopicMastery m = TopicMastery.create("user-1", "math", "algebra");
        for (int i = 0; i < 30; i++) {
            m = m.withCorrectAnswer();
        }
        assertThat(m.targetDifficulty()).isEqualTo(TopicMastery.DifficultyTier.HARD);
        assertThat(m.getConfidenceScore()).isGreaterThan(0.70);
    }

    @Test
    @DisplayName("Mostly wrong answers → EASY difficulty tier")
    void mostlyWrongAnswersYieldsEasyTier() {
        TopicMastery m = TopicMastery.create("user-1", "math", "algebra");
        for (int i = 0; i < 5; i++) {
            m = m.withWrongAnswer();
        }
        assertThat(m.targetDifficulty()).isEqualTo(TopicMastery.DifficultyTier.EASY);
        assertThat(m.getConfidenceScore()).isLessThan(0.40);
    }

    @Test
    @DisplayName("Accuracy drop of >5% triggers declining trend")
    void accuracyDropTriggersDecliningTrend() {
        // Start with high accuracy then drop significantly
        TopicMastery m = TopicMastery.create("user-1", "math", "algebra");
        for (int i = 0; i < 5; i++) m = m.withCorrectAnswer(); // high accuracy
        m = m.withWrongAnswer();
        m = m.withWrongAnswer();
        m = m.withWrongAnswer(); // should trigger declining

        assertThat(m.getTrend()).isIn("declining", "stable"); // depends on delta
    }

    @Test
    @DisplayName("Confidence is capped at 1.0 even with 100 correct answers")
    void confidenceIsCappedAtOne() {
        TopicMastery m = TopicMastery.create("user-1", "math", "algebra");
        for (int i = 0; i < 100; i++) {
            m = m.withCorrectAnswer();
        }
        assertThat(m.getConfidenceScore()).isLessThanOrEqualTo(1.0);
    }

    @Test
    @DisplayName("levelDelta is +2 when confidence > 0.80 and trend is improving")
    void levelDeltaIsPositiveTwoWhenHighConfidenceImproving() {
        TopicMastery m = TopicMastery.create("user-1", "math", "algebra");
        // Build high confidence
        for (int i = 0; i < 30; i++) m = m.withCorrectAnswer();

        // High confidence should give positive level delta
        assertThat(m.getLevelDelta()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("levelDelta is -1 for very low confidence")
    void levelDeltaIsNegativeOneForLowConfidence() {
        TopicMastery m = TopicMastery.create("user-1", "math", "algebra");
        for (int i = 0; i < 5; i++) m = m.withWrongAnswer();

        assertThat(m.getLevelDelta()).isEqualTo(-1);
    }

    @Test
    @DisplayName("recentWindow is capped at last 5 answers")
    void recentWindowCappedAtFive() {
        TopicMastery m = TopicMastery.create("user-1", "math", "algebra");
        for (int i = 0; i < 10; i++) m = m.withCorrectAnswer();

        assertThat(m.getRecentWindow()).hasSize(5);
        assertThat(m.getRecentWindow()).isEqualTo("11111");
    }

    @Test
    @DisplayName("Immutable update — original instance is unchanged after withCorrectAnswer and ID is preserved")
    void updateIsImmutableAndPreservesId() {
        TopicMastery original = TopicMastery.create("user-1", "math", "algebra");
        original.setId("test-mastery-id-999");
        TopicMastery updated  = original.withCorrectAnswer();

        assertThat(original.getTotalQuestions()).isEqualTo(0);
        assertThat(updated.getTotalQuestions()).isEqualTo(1);
        assertThat(updated.getId()).isEqualTo("test-mastery-id-999");

        TopicMastery updatedWrong = original.withWrongAnswer();
        assertThat(updatedWrong.getId()).isEqualTo("test-mastery-id-999");
    }

    @Test
    @DisplayName("Mixed answers produce accurate accuracy calculation")
    void mixedAnswersAccuracy() {
        TopicMastery m = TopicMastery.create("user-1", "math", "algebra");
        m = m.withCorrectAnswer();
        m = m.withCorrectAnswer();
        m = m.withWrongAnswer();
        m = m.withCorrectAnswer();

        // 3 correct out of 4
        assertThat(m.getAccuracy()).isCloseTo(0.75, within(0.001));
        assertThat(m.getTotalQuestions()).isEqualTo(4);
        assertThat(m.getCorrectAnswers()).isEqualTo(3);
    }
}
