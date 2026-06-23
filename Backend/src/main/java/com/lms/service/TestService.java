package com.lms.service;

import com.lms.model.Question;
import com.lms.model.ResponseRecord;
import com.lms.model.TestResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Orchestrates the test flow — delegates all intelligence to AdaptiveEngineService.
 *
 * Fixed:
 *   - Now accepts userId parameter (replaces hardcoded "user1")
 *   - Returns typed TestResponse instead of raw Map
 *   - simulateTestFlow returns Map<String,Object> wrapping TestResponse for backward compat
 */
@Service
public class TestService {

    @Autowired
    private AdaptiveEngineService adaptiveEngine;

    // ── Main method — used by controller ─────────────────────────────────

    public TestResponse getNextQuestion(
            String userId,
            List<Question> allQuestions,
            List<ResponseRecord> history
    ) {
        if (allQuestions == null || allQuestions.isEmpty()) {
            allQuestions = generateStarterQuestions();
        }
        if (history == null) history = new ArrayList<>();

        TestResponse result = adaptiveEngine.selectNextQuestion(userId, allQuestions, history);

        if (result == null) {
            throw new IllegalArgumentException("No suitable question found");
        }
        return result;
    }

    // ── Simulate flow (debug / integration tests) ─────────────────────────

    public Map<String, Object> simulateTestFlow(
            String userId,
            List<Question> questions,
            List<ResponseRecord> history
    ) {
        Map<String, Object> response = new HashMap<>();
        try {
            TestResponse next = getNextQuestion(userId, questions, history);
            response.put("nextQuestion", next);
        } catch (IllegalArgumentException e) {
            response.put("error", e.getMessage());
        }
        return response;
    }

    private List<Question> generateStarterQuestions() {
        return Arrays.asList(
            new Question("q-start-1", "What is the time complexity of binary search?", "Algorithms", 1, "O(log n)"),
            new Question("q-start-2", "What data structure uses LIFO?", "Data Structures", 1, "Stack"),
            new Question("q-start-3", "What is the space complexity of quicksort?", "Algorithms", 2, "O(log n)")
        );
    }
}
