package com.lms.repository;

import com.lms.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends MongoRepository<Notification, String> {

    /** All notifications for a user, newest first. */
    List<Notification> findByUserIdOrderByCreatedAtMsDesc(String userId);

    /** Paginated notifications for a user, newest first. */
    Page<Notification> findByUserIdOrderByCreatedAtMsDesc(String userId, Pageable pageable);

    /** Unread notifications for a user. */
    List<Notification> findByUserIdAndReadFalseOrderByCreatedAtMsDesc(String userId);

    /** Count unread notifications (for badge count). */
    long countByUserIdAndReadFalse(String userId);

    /** Delete all notifications for a user (GDPR / account deletion). */
    void deleteByUserId(String userId);

    /** Delete notifications by resourceId (e.g. sourceId). */
    void deleteByResourceId(String resourceId);
}
