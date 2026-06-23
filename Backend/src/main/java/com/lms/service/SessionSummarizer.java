package com.lms.service;

import com.lms.model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SessionSummarizer {

    private static final Logger LOG = LoggerFactory.getLogger(SessionSummarizer.class);

    private final com.lms.service.ai.AIOrchestratorService aiOrchestratorService;

    @Autowired
    public SessionSummarizer(com.lms.service.ai.AIOrchestratorService aiOrchestratorService) {
        this.aiOrchestratorService = aiOrchestratorService;
    }

    /**
     * Condense a list of conversation messages into a short 2-sentence summary paragraph.
     */
    public String summarize(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }

        LOG.info("Summarizing {} old chat messages to optimize token budget", messages.size());

        StringBuilder sb = new StringBuilder();
        sb.append("Summarize the following chat conversation between a Student and their AI Tutor. ");
        sb.append("Write a single, highly dense paragraph (max 3 sentences) capturing the core questions asked and concepts explained.\n\n");
        sb.append("CONVERSATION LOG:\n");

        for (ChatMessage msg : messages) {
            sb.append(msg.getSender()).append(": ").append(msg.getContent()).append("\n");
        }

        sb.append("\nSummary paragraph: ");

        try {
            return aiOrchestratorService.generate(sb.toString(), Map.of());
        } catch (Exception e) {
            LOG.error("Failed to generate conversation summary: {}", e.getMessage());
            return "The student and tutor discussed concept principles.";
        }
    }
}
