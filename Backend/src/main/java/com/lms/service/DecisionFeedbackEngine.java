package com.lms.service;

import com.lms.model.ResponseRecord;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Evaluates the impact of the system's last question selection decision
 * and tracks recent accuracy for adaptive difficulty tuning.
 *
 * All logic PRESERVED EXACTLY from original.
 */
@Service
public class DecisionFeedbackEngine {

    /**
     * Evaluate if the last decision improved learning.
     *
     * Returns:
     *   +1.0 — improvement (wrong → correct)
     *   -1.0 — decline     (correct → wrong)
     *    0.0 — stable or insufficient history
     */
    public double evaluateDecisionImpact(List<ResponseRecord> history) {

        if (history == null || history.size() < 2) return 0.0;

        int size = history.size();
        ResponseRecord prev = history.get(size - 2);
        ResponseRecord curr = history.get(size - 1);

        if (!prev.isCorrect() && curr.isCorrect())  return  1.0;  // good decision
        if ( prev.isCorrect() && !curr.isCorrect()) return -1.0;  // decline
        return 0.0;
    }

    /**
     * Calculate recent system performance over last N=5 answers.
     * Used by AdaptiveEngineService to tune difficulty.
     */
    public double calculateRecentAccuracy(List<ResponseRecord> history) {

        if (history == null || history.isEmpty()) return 0.0;

        int  lastN   = Math.min(5, history.size());
        long correct = history.subList(history.size() - lastN, history.size())
                .stream()
                .filter(ResponseRecord::isCorrect)
                .count();

        return (double) correct / lastN;
    }
}
