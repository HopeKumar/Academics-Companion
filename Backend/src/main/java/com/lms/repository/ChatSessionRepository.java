package com.lms.repository;

import com.lms.model.ChatSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatSessionRepository extends MongoRepository<ChatSession, String> {

    /**
     * Find all chat sessions for a specific user, sorted by most recently updated.
     */
    List<ChatSession> findByUserIdOrderByUpdatedAtDesc(String userId);

    Page<ChatSession> findByUserIdOrderByUpdatedAtDesc(String userId, Pageable pageable);

    /**
     * Find session by ID and user ID for security boundary check.
     */
    Optional<ChatSession> findByIdAndUserId(String id, String userId);
}
