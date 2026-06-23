package com.lms.service;

import com.lms.model.ChatMessage;
import com.lms.repository.ChatMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ConversationMemoryService {

    private static final Logger LOG = LoggerFactory.getLogger(ConversationMemoryService.class);

    private final ChatMessageRepository messageRepository;
    private final SessionSummarizer     sessionSummarizer;
    private static final int            MAX_DETAILED_MESSAGES = 4;

    @Autowired
    public ConversationMemoryService(ChatMessageRepository messageRepository,
                                     SessionSummarizer sessionSummarizer) {
        this.messageRepository = messageRepository;
        this.sessionSummarizer = sessionSummarizer;
    }

    public static class MemoryContext {
        private final String            summaryText;
        private final List<ChatMessage> detailedMessages;

        public MemoryContext(String summaryText, List<ChatMessage> detailedMessages) {
            this.summaryText      = summaryText;
            this.detailedMessages = detailedMessages;
        }

        public String            getSummaryText()      { return summaryText; }
        public List<ChatMessage> getDetailedMessages() { return detailedMessages; }
    }

    /**
     * Compile session history, auto-summarizing older parts if message count is high.
     */
    public MemoryContext loadActiveMemory(String sessionId, String userMsgIdToExclude) {
        List<ChatMessage> fullHistory = messageRepository.findBySessionIdOrderByTimestampAsc(sessionId);

        // Exclude the currently incoming message if it has been saved
        List<ChatMessage> pastMessages = new ArrayList<>();
        for (ChatMessage m : fullHistory) {
            if (userMsgIdToExclude == null || !m.getId().equals(userMsgIdToExclude)) {
                pastMessages.add(m);
            }
        }

        if (pastMessages.size() <= MAX_DETAILED_MESSAGES + 2) {
            return new MemoryContext("", pastMessages);
        }

        // Split history into old messages to summarize and recent detailed messages
        int splitIndex = pastMessages.size() - MAX_DETAILED_MESSAGES;
        List<ChatMessage> oldMessages = pastMessages.subList(0, splitIndex);
        List<ChatMessage> recentMessages = pastMessages.subList(splitIndex, pastMessages.size());

        // Summarize older messages
        String summary = sessionSummarizer.summarize(oldMessages);
        LOG.info("Session {} memory split completed. Summarized {} messages.", sessionId, oldMessages.size());

        return new MemoryContext(summary, recentMessages);
    }
}
