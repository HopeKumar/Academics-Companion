package com.lms.service;

import com.lms.model.ResponseRecord;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Predicts failure probability per concept and identifies the next weak concept.
 *
 * Converted from com.lms.adaptive.service → com.lms.service (package fixed).
 * ResponseRecord reference updated from com.lms.adaptive.model → com.lms.model.
 * All prediction logic PRESERVED EXACTLY.
 */
@Service
public class PredictionEngine {

    /**
     * Predict probability of failure per concept.
     * Returns: concept → failure weight (normalized by attempt count).
     */
    public Map<String, Double> predictFailureProbability(List<ResponseRecord> history) {

        Map<String, Double> conceptFailure = new HashMap<>();
        Map<String, Integer> counts        = new HashMap<>();

        for (ResponseRecord r : history) {
            String concept = r.getConcept();
            counts.put(concept, counts.getOrDefault(concept, 0) + 1);

            if (!r.isCorrect()) {
                conceptFailure.put(concept,
                        conceptFailure.getOrDefault(concept, 0.0) + r.getDifficulty());
            }
        }

        // Normalize by attempt count
        for (String concept : conceptFailure.keySet()) {
            double failures = conceptFailure.get(concept);
            int    total    = counts.getOrDefault(concept, 1);
            conceptFailure.put(concept, failures / total);
        }

        return conceptFailure;
    }

    /**
     * Predict the next risky concept (highest failure weight).
     * Returns null if history is empty.
     */
    public String predictNextWeakConcept(Map<String, Double> failureMap) {
        return failureMap.entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }
}
