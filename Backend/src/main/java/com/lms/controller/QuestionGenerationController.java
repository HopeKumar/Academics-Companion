package com.lms.controller;

import com.lms.model.*;
import com.lms.repository.QuestionRepository;
import com.lms.repository.ResponseRecordRepository;
import com.lms.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Map;
import java.util.List;

import com.lms.service.SourceService;

@RestController
@RequestMapping({"/quiz", "/api/v1/quiz"})
@Tag(name = "Quiz", description = "Adaptive Quiz Generation APIs")
public class QuestionGenerationController {

    private static final Logger LOG = LoggerFactory.getLogger(QuestionGenerationController.class);

    private final QuestionGenerationService questionGenService;
    private final QuestionRepository         questionRepo;
    private final ResponseRecordRepository   responseRepo;
    private final TopicMasteryService        masteryService;
    private final AnalyticsService           analyticsService;
    private final AdaptiveLearningService    adaptiveLearningService;
    private final SourceService              sourceService;

    @Autowired
    public QuestionGenerationController(QuestionGenerationService questionGenService,
                                        QuestionRepository questionRepo,
                                        ResponseRecordRepository responseRepo,
                                        TopicMasteryService masteryService,
                                        AnalyticsService analyticsService,
                                        AdaptiveLearningService adaptiveLearningService,
                                        SourceService sourceService) {
        this.questionGenService = questionGenService;
        this.questionRepo       = questionRepo;
        this.responseRepo       = responseRepo;
        this.masteryService     = masteryService;
        this.analyticsService   = analyticsService;
        this.adaptiveLearningService = adaptiveLearningService;
        this.sourceService      = sourceService;
    }

    // ── POST /quiz/generate-question ──────────────────────────────────────

    @PostMapping("/generate-question")
    @Operation(summary = "Generate a single quiz question", description = "Generates an adaptive question based on mastery and concept.")
    public ResponseEntity<?> generateQuestion(@Valid @RequestBody GenerateQuestionRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authServiceUser();

        LOG.info("POST /quiz/generate-question user={} concept={} difficulty={} sourceId={}",
                username, request.getConcept(), request.getDifficulty(), request.getSourceId());

        if (request.getSourceId() == null || request.getSourceId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "sourceId is required"));
        }

        try {
            // Validate ownership synchronously before generation
            sourceService.getSourceStatus(user.getId(), request.getSourceId());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }

        Map<String, Object> statusMap = sourceService.getSourceStatus(user.getId(), request.getSourceId());
        if ("PROCESSING".equals(statusMap.get("status"))) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.ACCEPTED)
                    .body(Map.of("status", "PROCESSING", "message", "Quiz is still generating..."));
        }

        try {
            String concept = request.getConcept() != null && !request.getConcept().isBlank() ? request.getConcept() : "General";

            // Dynamically override requested difficulty based on past performance mastery tier
            TopicMastery mastery = masteryService.getMastery(user.getId(), concept);
            int diff = mastery.targetDifficulty().ordinal() + 1; // 1=EASY, 2=MEDIUM, 3=HARD

            LOG.info("Adaptive Engine: User {} Concept {} Mastery Tier {} -> Selected Diff {}", user.getId(), concept, mastery.targetDifficulty(), diff);

            QuestionGenerationService.GeneratedQuestionWrapper result = questionGenService.generateQuestion(
                    user.getId(), concept, diff, request.getSourceId(), "MCQ"
            );

            return ResponseEntity.ok(Map.of(
                    "question", result.getQuestion(),
                    "explanation", result.getExplanation()
            ));
        } catch (Exception e) {
            LOG.error("Failed to generate quiz question: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "Question generation failed: " + e.getMessage()));
        }
    }

    // ── POST /quiz/generate-adaptive ──────────────────────────────────────

    @PostMapping("/generate-adaptive")
    @Operation(summary = "Generate a full adaptive quiz", description = "Generates multiple questions targeting the user's weak concepts.")
    public ResponseEntity<?> generateAdaptiveQuiz(@Valid @RequestBody GenerateAdaptiveQuizRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authServiceUser();

        LOG.info("POST /quiz/generate-adaptive user={} concept={} sourceId={} count={}",
                username, request.getConcept(), request.getSourceId(), request.getTotalQuestions());

        if (request.getSourceId() == null || request.getSourceId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "sourceId is required"));
        }

        try {
            // Validate ownership synchronously before generation
            sourceService.getSourceStatus(user.getId(), request.getSourceId());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }

        Map<String, Object> statusMap2 = sourceService.getSourceStatus(user.getId(), request.getSourceId());
        if ("PROCESSING".equals(statusMap2.get("status"))) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.ACCEPTED)
                    .body(Map.of("status", "PROCESSING", "message", "Quiz is still generating..."));
        }

        try {
            String concept = request.getConcept() != null && !request.getConcept().isBlank() ? request.getConcept() : "General";
            int count = request.getTotalQuestions() > 0 ? request.getTotalQuestions() : 5;
            
            List<QuestionGenerationService.GeneratedQuestionWrapper> quiz = questionGenService.generateQuizForStudent(
                    user.getId(), concept, request.getSourceId(), count
            );
            
            return ResponseEntity.ok(quiz);
        } catch (Exception e) {
            LOG.error("Failed to generate adaptive quiz: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "Quiz generation failed: " + e.getMessage()));
        }
    }

    // ── POST /quiz/submit ─────────────────────────────────────────────────

    @PostMapping("/submit")
    public ResponseEntity<?> submitAnswer(@Valid @RequestBody SubmitAnswerRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authServiceUser();

        LOG.info("POST /quiz/submit user={} questionId={} answer={}",
                username, request.getQuestionId(), request.getStudentAnswer());

        try {
            Question question = questionRepo.findById(request.getQuestionId())
                    .orElseThrow(() -> new IllegalArgumentException("Question not found"));

            // Check if correct
            String studentAns = request.getStudentAnswer() != null ? request.getStudentAnswer().trim().toUpperCase() : "";
            String correctAns = question.getCorrectAnswer() != null ? question.getCorrectAnswer().trim().toUpperCase() : "";
            
            // Map student letter ("A", "B", "C", "D") to index (0, 1, 2, 3)
            int studentIndex = -1;
            if (studentAns.length() == 1) {
                studentIndex = studentAns.charAt(0) - 'A';
            }
            
            boolean correct = studentAns.equals(correctAns);
            if (!correct && studentIndex >= 0 && question.getCorrectOptionIndex() != null) {
                correct = (studentIndex == question.getCorrectOptionIndex());
            }

            // Record Response in MongoDB
            ResponseRecord record = new ResponseRecord(
                    question.getId(),
                    question.getConcept(),
                    question.getDifficulty(),
                    correct
            );
            record.setUserId(user.getId());
            record.setStudentAnswer(studentAns);
            responseRepo.save(record);

            // Update Topic Mastery in MongoDB
            TopicMastery mastery = correct
                    ? masteryService.recordCorrect(user.getId(), question.getConcept())
                    : masteryService.recordWrong(user.getId(), question.getConcept());

            // Track performance in adaptive profile
            adaptiveLearningService.trackQuizPerformance(user.getId(), correct ? 1.0 : 0.0);

            // Clear cache to force aggregate refresh
            analyticsService.invalidateCache(user.getId());

            return ResponseEntity.ok(Map.of(
                    "correct", correct,
                    "correctAnswer", correctAns,
                    "concept", question.getConcept(),
                    "confidenceScore", mastery.getConfidenceScore(),
                    "accuracy", mastery.getAccuracy(),
                    "streak", analyticsService.calculateDailyStreak(user.getId())
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            LOG.error("Failed to submit quiz answer: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "Submission processing failed: " + e.getMessage()));
        }
    }

    // ── Helper User Fetcher ───────────────────────────────────────────────

    @Autowired
    private AuthService authService;

    private User authServiceUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return authService.getUserByUsername(username);
    }

    // ── DTOs ──────────────────────────────────────────────────────────────

    public static class GenerateQuestionRequest {
        @NotBlank(message = "Concept cannot be blank")
        private String concept;

        private int    difficulty;
        private String sourceId;

        public String getConcept() { return concept; }
        public void setConcept(String concept) { this.concept = concept; }
        public int getDifficulty() { return difficulty; }
        public void setDifficulty(int difficulty) { this.difficulty = difficulty; }
        public String getSourceId() { return sourceId; }
        public void setSourceId(String sourceId) { this.sourceId = sourceId; }
    }

    public static class SubmitAnswerRequest {
        @NotBlank(message = "QuestionId cannot be blank")
        private String questionId;

        @NotBlank(message = "Student answer cannot be blank")
        private String studentAnswer;

        public String getQuestionId() { return questionId; }
        public void setQuestionId(String questionId) { this.questionId = questionId; }
        public String getStudentAnswer() { return studentAnswer; }
        public void setStudentAnswer(String studentAnswer) { this.studentAnswer = studentAnswer; }
    }

    public static class GenerateAdaptiveQuizRequest {
        @NotBlank(message = "Concept cannot be blank")
        private String concept;
        private String sourceId;
        private int totalQuestions;

        public String getConcept() { return concept; }
        public void setConcept(String concept) { this.concept = concept; }
        public String getSourceId() { return sourceId; }
        public void setSourceId(String sourceId) { this.sourceId = sourceId; }
        public int getTotalQuestions() { return totalQuestions; }
        public void setTotalQuestions(int totalQuestions) { this.totalQuestions = totalQuestions; }
    }
}
