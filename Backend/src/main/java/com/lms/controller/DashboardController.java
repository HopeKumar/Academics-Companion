package com.lms.controller;

import com.lms.model.User;
import com.lms.service.AnalyticsService;
import com.lms.service.AuthService;
import com.lms.service.RecommendationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lms.model.StudentDashboardResponse;
import com.lms.model.FacultyDashboardResponse;

import java.util.HashMap;
import java.util.Map;

@RestController
public class DashboardController {

    private static final Logger LOG = LoggerFactory.getLogger(DashboardController.class);

    private final AnalyticsService analyticsService;
    private final RecommendationService recommendationService;
    private final AuthService authService;

    @Autowired
    public DashboardController(AnalyticsService analyticsService,
                               RecommendationService recommendationService,
                               AuthService authService) {
        this.analyticsService = analyticsService;
        this.recommendationService = recommendationService;
        this.authService = authService;
    }

    @GetMapping({"/dashboard", "/api/v1/dashboard"})
    public ResponseEntity<?> getDashboard() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);

        LOG.info("GET /dashboard user={}", username);
        try {
            Map<String, Object> analytics = analyticsService.getAnalytics(user.getId());
            Map<String, Object> recommendations = recommendationService.recommend(user.getId());

            Map<String, Object> dashboard = new HashMap<>();
            dashboard.put("stats", analytics);
            dashboard.put("recommendations", recommendations);
            
            return ResponseEntity.ok(dashboard);
        } catch (Exception e) {
            LOG.error("Failed to compile dashboard: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to retrieve dashboard: " + e.getMessage()));
        }
    }
    
    @GetMapping({"/api/dashboard/student/{id}", "/api/v1/dashboard/student/{id}"})
    public ResponseEntity<StudentDashboardResponse> getStudentDashboard(@PathVariable String id) {
        LOG.info("GET /api/dashboard/student/{}", id);
        return ResponseEntity.ok(analyticsService.getStudentDashboard(id));
    }
    
    @PreAuthorize("hasRole('FACULTY')")
    @GetMapping({"/api/dashboard/faculty", "/api/v1/dashboard/faculty"})
    public ResponseEntity<FacultyDashboardResponse> getFacultyDashboard() {
        LOG.info("GET /api/dashboard/faculty");
        return ResponseEntity.ok(analyticsService.getFacultyDashboard());
    }
}
