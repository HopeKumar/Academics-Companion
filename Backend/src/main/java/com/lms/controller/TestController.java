package com.lms.controller;

import com.lms.metrics.MetricsRegistry;
import com.lms.model.*;
import com.lms.repository.QuestionRepository;
import com.lms.repository.ResponseRecordRepository;
import com.lms.service.TestService;
import com.lms.service.TopicMasteryService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * REST controller for adaptive test flow.
 *
 * Fixed:
 *   - Raw Map request/response replaced with proper DTOs (TestRequest, TestResponse)
 *   - Inline records removed (moved to com.lms.model)
 *   - @Valid validation added on all request bodies
 *   - userId from request body — hardcoded "user1" removed
 *   - Null-safe history defaulting
 *   - All business logic delegated to TestService / TopicMasteryService
 *
 * Endpoints:
 *   POST /test/next         — select next question adaptively
 *   POST /test/simulate     — simulate test flow (debug/integration)
 *   POST /test/mastery      — record an answer and update topic mastery
 */
@RestController
@RequestMapping({"/test", "/api/v1/test"})
public class TestController {

    private static final Logger LOG = LoggerFactory.getLogger(TestController.class);

    private final TestService              testService;
    private final TopicMasteryService       masteryService;
    private final MetricsRegistry           metrics;
    private final QuestionRepository        questionRepository;
    private final ResponseRecordRepository  responseRepository;

    @Autowired
    public TestController(TestService testService,
                          TopicMasteryService masteryService,
                          MetricsRegistry metrics,
                          QuestionRepository questionRepository,
                          ResponseRecordRepository responseRepository) {
        this.testService        = testService;
        this.masteryService     = masteryService;
        this.metrics            = metrics;
        this.questionRepository = questionRepository;
        this.responseRepository = responseRepository;
    }

    // ── POST /test/next ───────────────────────────────────────────────────

    /**
     * Select the next question adaptively for the given student.
     *
     * Request body:
     * {
     *   "userId":    "student-001",
     *   "questions": [ { "id": "q1", "text": "...", "concept": "BST",
     *                    "difficulty": 2, "correctAnswer": "..." } ],
     *   "history":   [ { "questionId": "q1", "concept": "BST",
     *                    "difficulty": 2, "correct": true } ]
     * }
     *
     * Response:
     * { "question": { ... }, "reason": "Selected based on adaptive difficulty level 2" }
     */
    @PostMapping("/next")
    public ResponseEntity<TestResponse> getNextQuestion(
            @Valid @RequestBody TestRequest request
    ) {
        long start = System.currentTimeMillis();

        // MongoDB Fallbacks
        List<Question> questions = request.getQuestions();
        if (questions == null || questions.isEmpty()) {
            questions = questionRepository.findAll();
        }

        List<ResponseRecord> history = request.getHistory();
        if (history == null || history.isEmpty()) {
            history = responseRepository.findByUserId(request.getUserId());
        }

        LOG.info("POST /test/next userId={} questions={} historySize={}",
                request.getUserId(),
                questions != null ? questions.size() : 0,
                history != null ? history.size() : 0);

        try {
            TestResponse result = testService.getNextQuestion(
                    request.getUserId(), questions, history);

            long elapsed = System.currentTimeMillis() - start;
            metrics.recordRequest("/test/next", elapsed, false);
            if (elapsed > 2_000) {
                LOG.warn("SLOW REQUEST /test/next elapsed={}ms", elapsed);
                metrics.recordSlowRequest("/test/next", elapsed);
            }

            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException | com.lms.exception.ResourceNotFoundException e) {
            LOG.error("Bad request to adaptive logic: {}", e.getMessage());
            metrics.recordRequest("/test/next", System.currentTimeMillis() - start, true);
            throw e;
        } catch (Exception e) {
            LOG.error("Failed to get next question: {}", e.getMessage(), e);
            metrics.recordRequest("/test/next", System.currentTimeMillis() - start, true);
            throw new com.lms.exception.AIServiceException("Failed to evaluate adaptive test logic: " + e.getMessage());
        }
    }

    // ── POST /test/simulate ───────────────────────────────────────────────

    /**
     * Simulate a full test flow step. Useful for integration testing.
     * Same request body as /test/next.
     */
    @PostMapping("/simulate")
    public ResponseEntity<Map<String, Object>> simulateTestFlow(
            @Valid @RequestBody TestRequest request
    ) {
        long start = System.currentTimeMillis();
        LOG.info("POST /test/simulate userId={}", request.getUserId());

        List<Question> questions = request.getQuestions();
        if (questions == null || questions.isEmpty()) {
            questions = questionRepository.findAll();
        }

        List<ResponseRecord> history = request.getHistory();
        if (history == null || history.isEmpty()) {
            history = responseRepository.findByUserId(request.getUserId());
        }

        try {
            Map<String, Object> result = testService.simulateTestFlow(
                    request.getUserId(), questions, history);

            metrics.recordRequest("/test/simulate", System.currentTimeMillis() - start, false);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException | com.lms.exception.ResourceNotFoundException e) {
            LOG.error("Bad request to simulate flow: {}", e.getMessage());
            metrics.recordRequest("/test/simulate", System.currentTimeMillis() - start, true);
            throw e;
        } catch (Exception e) {
            LOG.error("Failed to simulate test flow: {}", e.getMessage(), e);
            metrics.recordRequest("/test/simulate", System.currentTimeMillis() - start, true);
            throw new com.lms.exception.AIServiceException("Simulation failed: " + e.getMessage());
        }
    }

    // ── POST /test/mastery ────────────────────────────────────────────────

    /**
     * Record an answer and update topic mastery.
     *
     * Request body:
     * { "studentId": "S1", "topic": "BST", "correct": true }
     *
     * Response: updated TopicMastery document.
     */
    @PostMapping("/mastery")
    public ResponseEntity<?> recordMastery(
            @Valid @RequestBody MasteryRequest request
    ) {
        long start = System.currentTimeMillis();
        LOG.info("POST /test/mastery student={} topic={} correct={}",
                request.getStudentId(), request.getTopic(), request.isCorrect());

        try {
            Object updated = request.isCorrect()
                    ? masteryService.recordCorrect(request.getStudentId(), request.getTopic())
                    : masteryService.recordWrong(request.getStudentId(), request.getTopic());

            metrics.recordRequest("/test/mastery", System.currentTimeMillis() - start, false);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException | com.lms.exception.ResourceNotFoundException e) {
            LOG.error("Bad request to mastery: {}", e.getMessage());
            metrics.recordRequest("/test/mastery", System.currentTimeMillis() - start, true);
            throw e;
        } catch (Exception e) {
            LOG.error("Failed to update mastery: {}", e.getMessage(), e);
            metrics.recordRequest("/test/mastery", System.currentTimeMillis() - start, true);
            throw new com.lms.exception.AIServiceException("Mastery update failed: " + e.getMessage());
        }
    }
}
