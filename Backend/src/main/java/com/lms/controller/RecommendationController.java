package com.lms.controller;

import com.lms.metrics.MetricsRegistry;
import com.lms.service.RecommendationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import com.lms.service.AuthService;
import com.lms.model.User;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for the recommendation engine.
 *
 * Replaces the Vert.x RecommendationController (Router + RoutingContext).
 * Zero business logic — delegates entirely to RecommendationService.
 *
 * Endpoint:
 *   GET /recommendations?studentId=S1
 */
@RestController
public class RecommendationController {

    private static final Logger LOG = LoggerFactory.getLogger(RecommendationController.class);

    private final RecommendationService recommendationService;
    private final MetricsRegistry       metrics;
    private final AuthService           authService;

    @Autowired
    public RecommendationController(RecommendationService recommendationService,
                                     MetricsRegistry metrics,
                                     AuthService authService) {
        this.recommendationService = recommendationService;
        this.metrics               = metrics;
        this.authService           = authService;
    }

    // ── GET /recommendations ──────────────────────────────────────────────

    @GetMapping({"/recommendations", "/api/v1/recommendations"})
    public ResponseEntity<?> getRecommendations() {
        long start = System.currentTimeMillis();

        String username = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);
        String studentId = user.getId();

        LOG.info("GET /recommendations student={}", studentId);

        try {
            com.lms.model.RecommendationResponse result = recommendationService.getPersonalizedRecommendations(studentId);
            long elapsed = System.currentTimeMillis() - start;

            metrics.recordRequest("/recommendations", elapsed, false);

            if (elapsed > 2_000) {
                LOG.warn("SLOW REQUEST /recommendations student={} elapsed={}ms", studentId, elapsed);
                metrics.recordSlowRequest("/recommendations", elapsed);
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            metrics.recordRequest("/recommendations", elapsed, true);
            LOG.error("RECOMMENDATIONS ERROR student={}: {}", studentId, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ── GET /api/recommendations/{studentId} ──────────────────────────────

    @GetMapping({"/api/recommendations/{studentId}", "/api/v1/recommendations/{studentId}"})
    public ResponseEntity<com.lms.model.RecommendationResponse> getPersonalizedRecommendations(@PathVariable String studentId) {
        LOG.info("GET /api/recommendations/{}", studentId);
        com.lms.model.RecommendationResponse response = recommendationService.getPersonalizedRecommendations(studentId);
        return ResponseEntity.ok(response);
    }

}
