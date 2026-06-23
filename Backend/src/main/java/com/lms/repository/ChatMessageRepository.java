package com.lms.repository;

import com.lms.model.ChatMessage;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {

    /**
     * Find all messages within a chat session, in chronological order.
     */
    List<ChatMessage> findBySessionIdOrderByTimestampAsc(String sessionId);

    /**
     * Delete all messages associated with a specific chat session.
     */
    void deleteBySessionId(String sessionId);
}
