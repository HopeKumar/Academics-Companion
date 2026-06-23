package com.lms.controller;

import com.lms.model.Notification;
import com.lms.model.User;
import com.lms.service.AuthService;
import com.lms.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for user notifications.
 *
 * Endpoints:
 *   GET  /notifications                    — list all (pageable)
 *   GET  /notifications/unread             — unread only
 *   GET  /notifications/unread-count       — badge count
 *   GET  /notifications/summary            — unread count + 5 recent
 *   POST /notifications/{id}/read          — mark one as read
 *   POST /notifications/read-all           — mark all as read
 */
@RestController
@RequestMapping({"/notifications", "/api/v1/notifications"})
public class NotificationController {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationController.class);

    private final NotificationService notificationService;
    private final AuthService         authService;

    @Autowired
    public NotificationController(NotificationService notificationService,
                                  AuthService authService) {
        this.notificationService = notificationService;
        this.authService         = authService;
    }

    // ── GET /notifications ────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<?> getNotifications(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        String userId = getCurrentUserId();

        if (page == 0 && size == 20) {
            // Fast path — return all without pagination overhead
            List<Notification> all = notificationService.getNotifications(userId);
            return ResponseEntity.ok(all);
        }

        Page<Notification> paged = notificationService.getNotifications(userId, page, size);
        return ResponseEntity.ok(Map.of(
                "content",       paged.getContent(),
                "totalElements", paged.getTotalElements(),
                "totalPages",    paged.getTotalPages(),
                "currentPage",   paged.getNumber()
        ));
    }

    // ── GET /notifications/unread ─────────────────────────────────────────

    @GetMapping("/unread")
    public ResponseEntity<List<Notification>> getUnread() {
        String userId = getCurrentUserId();
        return ResponseEntity.ok(notificationService.getUnreadNotifications(userId));
    }

    // ── GET /notifications/unread-count ───────────────────────────────────

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount() {
        String userId = getCurrentUserId();
        long count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }

    // ── GET /notifications/summary ────────────────────────────────────────

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        String userId = getCurrentUserId();
        return ResponseEntity.ok(notificationService.getNotificationSummary(userId));
    }

    // ── POST /notifications/{id}/read ─────────────────────────────────────

    @PostMapping("/{id}/read")
    public ResponseEntity<?> markAsRead(@PathVariable String id) {
        String userId = getCurrentUserId();
        try {
            Notification updated = notificationService.markAsRead(userId, id);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── POST /notifications/read-all ──────────────────────────────────────

    @PostMapping("/read-all")
    public ResponseEntity<Map<String, Object>> markAllRead() {
        String userId = getCurrentUserId();
        int updated = notificationService.markAllRead(userId);
        LOG.info("Marked all {} notifications read for userId={}", updated, userId);
        return ResponseEntity.ok(Map.of(
                "message", "Marked " + updated + " notifications as read.",
                "updated", updated
        ));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String getCurrentUserId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);
        return user.getId();
    }
}
