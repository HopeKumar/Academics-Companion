package com.lms.controller;

import com.lms.model.StudyPlan;
import com.lms.model.StudyPlanRequest;
import com.lms.model.User;
import com.lms.service.AuthService;
import com.lms.service.StudyPlanService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Map;

@RestController
@RequestMapping({"/api/study-plan", "/api/v1/study-plan"})
@Tag(name = "Study Plan", description = "AI Study Planner APIs")
public class StudyPlanController {

    private static final Logger LOG = LoggerFactory.getLogger(StudyPlanController.class);

    private final StudyPlanService studyPlanService;
    private final AuthService authService;

    @Autowired
    public StudyPlanController(StudyPlanService studyPlanService, AuthService authService) {
        this.studyPlanService = studyPlanService;
        this.authService = authService;
    }

    @PostMapping("/generate")
    @Operation(summary = "Generate a new AI study plan", description = "Generates a customized study plan based on student mastery and available time.")
    public ResponseEntity<?> generateStudyPlan(@Valid @RequestBody StudyPlanRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);

        LOG.info("POST /api/study-plan/generate user={} availableTime={}", username, request.getAvailableTime());

        try {
            StudyPlan plan = studyPlanService.generateStudyPlan(user.getId(), request.getAvailableTime());
            return ResponseEntity.ok(plan);
        } catch (Exception e) {
            LOG.error("Failed to generate study plan: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to generate study plan: " + e.getMessage()));
        }
    }

    @GetMapping("/{studentId}")
    @Operation(summary = "Get current study plan", description = "Retrieves the user's previously generated AI study plan.")
    public ResponseEntity<?> getStudyPlan(@PathVariable String studentId) {
        LOG.info("GET /api/study-plan/{}", studentId);
        try {
            StudyPlan plan = studyPlanService.getStudyPlan(studentId);
            return ResponseEntity.ok(plan);
        } catch (Exception e) {
            LOG.error("Failed to retrieve study plan: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/regenerate")
    @Operation(summary = "Regenerate study plan", description = "Forces the generation of a fresh study plan, replacing the existing one.")
    public ResponseEntity<?> regenerateStudyPlan(@Valid @RequestBody StudyPlanRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);

        LOG.info("PUT /api/study-plan/regenerate user={} availableTime={}", username, request.getAvailableTime());

        try {
            StudyPlan plan = studyPlanService.regenerateStudyPlan(user.getId(), request.getAvailableTime());
            return ResponseEntity.ok(plan);
        } catch (Exception e) {
            LOG.error("Failed to regenerate study plan: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to regenerate study plan: " + e.getMessage()));
        }
    }
}
