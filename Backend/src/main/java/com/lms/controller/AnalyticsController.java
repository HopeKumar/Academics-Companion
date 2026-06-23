package com.lms.controller;

import com.lms.model.ResponseRecord;
import com.lms.model.User;
import com.lms.repository.ResponseRecordRepository;
import com.lms.service.AuthService;
import com.lms.service.AnalyticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping
public class AnalyticsController {

    private static final Logger LOG = LoggerFactory.getLogger(AnalyticsController.class);

    private final AnalyticsService          analyticsService;
    private final ResponseRecordRepository responseRepo;
    private final AuthService              authService;

    @Autowired
    public AnalyticsController(AnalyticsService analyticsService,
                               ResponseRecordRepository responseRepo,
                               AuthService authService) {
        this.analyticsService = analyticsService;
        this.responseRepo     = responseRepo;
        this.authService       = authService;
    }

    // ── GET /analytics ────────────────────────────────────────────────────

    @GetMapping({"/analytics", "/api/v1/analytics"})
    public ResponseEntity<?> getAnalytics() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);

        LOG.info("GET /analytics user={}", username);
        try {
            Map<String, Object> data = analyticsService.getAnalytics(user.getId());
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            LOG.error("Failed to compile analytics: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to retrieve analytics: " + e.getMessage()));
        }
    }

    // ── GET /progress ─────────────────────────────────────────────────────

    @GetMapping({"/progress", "/api/v1/progress"})
    public ResponseEntity<?> getProgress() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);

        LOG.info("GET /progress user={}", username);
        try {
            List<ResponseRecord> progressHistory = responseRepo.findByUserIdOrderByTimestampAsc(user.getId());
            return ResponseEntity.ok(progressHistory);
        } catch (Exception e) {
            LOG.error("Failed to compile progress history: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to retrieve progress timeline: " + e.getMessage()));
        }
    }
}
