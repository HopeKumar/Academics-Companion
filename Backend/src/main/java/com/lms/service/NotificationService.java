package com.lms.service;

import com.lms.model.Notification;
import com.lms.model.Notification.Type;
import com.lms.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Manages user notifications for all platform events.
 *
 * Called by:
 *   - IngestionPipeline     → UPLOAD_COMPLETE / UPLOAD_FAILED
 *   - RecommendationService → RECOMMENDATION / AI_ALERT
 *   - AnalyticsService      → MILESTONE (streak, mastery achievements)
 *
 * Frontend polls GET /notifications and GET /notifications/unread-count.
 */
@Service
public class NotificationService {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    // ── Creation ──────────────────────────────────────────────────────────

    /**
     * Create and persist a notification.
     *
     * @param userId  The recipient user's ID.
     * @param type    Notification type (one of the Type enum values).
     * @param title   Short title (shown as header in the UI).
     * @param message Detailed message body.
     * @return The persisted Notification.
     */
    public Notification createNotification(String userId, Type type, String title, String message) {
        Notification notification = new Notification(userId, type, title, message);
        Notification saved = notificationRepository.save(notification);
        LOG.info("Notification created: userId={} type={} title={}", userId, type, title);
        return saved;
    }

    /**
     * Create a notification with an optional link to a specific resource.
     */
    public Notification createNotification(String userId, Type type, String title, String message,
                                           String resourceType, String resourceId) {
        Notification notification = new Notification(userId, type, title, message);
        notification.setResourceType(resourceType);
        notification.setResourceId(resourceId);
        return notificationRepository.save(notification);
    }

    // ── Convenience factory methods ───────────────────────────────────────

    public void notifyUploadComplete(String userId, String sourceId, String filename) {
        createNotification(userId, Type.UPLOAD_COMPLETE,
                "Document Ready",
                "Your document \"" + filename + "\" has been processed and is ready to study.",
                "Source", sourceId);
    }

    public void notifyUploadFailed(String userId, String filename, String reason) {
        createNotification(userId, Type.UPLOAD_FAILED,
                "Upload Failed",
                "Failed to process \"" + filename + "\". Reason: " + reason);
    }

    public void notifyMilestone(String userId, String title, String message) {
        createNotification(userId, Type.MILESTONE, title, message);
    }

    public void notifyAiAlert(String userId, String topic, String insight) {
        createNotification(userId, Type.AI_ALERT,
                "Learning Alert: " + topic, insight);
    }

    public void notifyRecommendation(String userId, String title, String message) {
        createNotification(userId, Type.RECOMMENDATION, title, message);
    }

    // ── Retrieval ─────────────────────────────────────────────────────────

    /** Get all notifications for a user, newest first. */
    public List<Notification> getNotifications(String userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtMsDesc(userId);
    }

    /** Get paginated notifications. */
    public Page<Notification> getNotifications(String userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return notificationRepository.findByUserIdOrderByCreatedAtMsDesc(userId, pageable);
    }

    /** Get unread notifications only. */
    public List<Notification> getUnreadNotifications(String userId) {
        return notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtMsDesc(userId);
    }

    /** Get unread notification count (for UI badge). */
    public long getUnreadCount(String userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    // ── Mark as read ──────────────────────────────────────────────────────

    /**
     * Mark a single notification as read.
     *
     * @throws IllegalArgumentException if the notification doesn't exist or belongs to another user.
     */
    public Notification markAsRead(String userId, String notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + notificationId));

        if (!notification.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Notification does not belong to this user.");
        }

        notification.setRead(true);
        return notificationRepository.save(notification);
    }

    /**
     * Mark all notifications for a user as read.
     * @return The number of notifications updated.
     */
    public int markAllRead(String userId) {
        List<Notification> unread = notificationRepository
                .findByUserIdAndReadFalseOrderByCreatedAtMsDesc(userId);

        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);

        LOG.info("Marked {} notifications as read for userId={}", unread.size(), userId);
        return unread.size();
    }

    // ── Summary ───────────────────────────────────────────────────────────

    public Map<String, Object> getNotificationSummary(String userId) {
        long unreadCount = getUnreadCount(userId);
        List<Notification> recent = notificationRepository
                .findByUserIdOrderByCreatedAtMsDesc(userId)
                .stream().limit(5).toList();

        return Map.of(
                "unreadCount", unreadCount,
                "recent",      recent
        );
    }
}
