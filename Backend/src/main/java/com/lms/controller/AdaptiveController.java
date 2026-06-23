package com.lms.controller;

import com.lms.model.StudentLearningProfile;
import com.lms.service.AdaptiveLearningService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/adaptive", "/api/v1/adaptive"})
@CrossOrigin(origins = "*")
public class AdaptiveController {

    private final AdaptiveLearningService adaptiveLearningService;
    private final com.lms.service.AuthService authService;

    @Autowired
    public AdaptiveController(AdaptiveLearningService adaptiveLearningService, com.lms.service.AuthService authService) {
        this.adaptiveLearningService = adaptiveLearningService;
        this.authService = authService;
    }

    private String resolveStudentId(String studentId) {
        if ("current-user-id".equals(studentId)) {
            String username = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
            return authService.getUserByUsername(username).getId();
        }
        return studentId;
    }

    @GetMapping("/profile/{studentId}")
    public ResponseEntity<StudentLearningProfile> getProfile(@PathVariable String studentId) {
        String resolvedId = resolveStudentId(studentId);
        StudentLearningProfile profile = adaptiveLearningService.getProfile(resolvedId);
        // Refresh trends whenever profile is fetched
        adaptiveLearningService.updateTrends(profile);
        return ResponseEntity.ok(profile);
    }

    @GetMapping("/recommendations/{studentId}")
    public ResponseEntity<Map<String, Object>> getRecommendations(@PathVariable String studentId) {
        String resolvedId = resolveStudentId(studentId);
        List<String> recommendations = adaptiveLearningService.getRecommendations(resolvedId);
        StudentLearningProfile profile = adaptiveLearningService.getProfile(resolvedId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("studentId", resolvedId);
        response.put("level", profile.getCurrentLevel().toString());
        response.put("recommendations", recommendations);
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/progress/{studentId}")
    public ResponseEntity<Map<String, Object>> getProgress(@PathVariable String studentId) {
        String resolvedId = resolveStudentId(studentId);
        StudentLearningProfile profile = adaptiveLearningService.getProfile(resolvedId);
        adaptiveLearningService.updateTrends(profile); // ensure trends are up to date
        
        Map<String, Object> progress = new HashMap<>();
        progress.put("knowledgeGrowthTrend", profile.getKnowledgeGrowthTrend());
        progress.put("subjectMasteryTrend", profile.getSubjectMasteryTrend());
        progress.put("topicMasteryTrend", profile.getTopicMasteryTrend());
        
        return ResponseEntity.ok(progress);
    }
}
