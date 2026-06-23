package com.lms.controller;

import com.lms.model.ChatMessage;
import com.lms.model.ChatSession;
import com.lms.model.User;
import com.lms.service.AuthService;
import com.lms.service.ChatService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/chat", "/api/v1/chat"})
public class ChatController {

    private static final Logger LOG = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;
    private final AuthService authService;

    @Autowired
    public ChatController(ChatService chatService, AuthService authService) {
        this.chatService = chatService;
        this.authService = authService;
    }

    // ── GET /chat/sessions ────────────────────────────────────────────────

    @GetMapping("/sessions")
    public ResponseEntity<List<ChatSession>> getSessions() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);

        LOG.info("GET /chat/sessions user={}", username);
        List<ChatSession> sessions = chatService.getSessionsForUser(user.getId());
        return ResponseEntity.ok(sessions);
    }

    // ── POST /chat ────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<?> sendMessage(@Valid @RequestBody ChatRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);

        LOG.info("POST /chat user={} sessionId={} content={}",
                username, request.getSessionId(), request.getContent().substring(0, Math.min(20, request.getContent().length())) + "...");

        try {
            ChatMessage response = chatService.sendMessage(
                    user.getId(),
                    request.getSessionId(),
                    request.getContent(),
                    request.getSourceIds()
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LOG.error("Failed to process chat message: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to get tutor response: " + e.getMessage()));
        }
    }

    // ── POST /chat/stream ─────────────────────────────────────────────────

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(@Valid @RequestBody ChatRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);

        LOG.info("STREAM_REQUEST_RECEIVED user={} sessionId={} content={}",
                username, request.getSessionId(), request.getContent() != null ? request.getContent().substring(0, Math.min(20, request.getContent().length())) + "..." : "null");

        if (request.getSessionId() == null || request.getSessionId().isBlank()) {
            throw new IllegalArgumentException("sessionId is required");
        }

        return chatService.sendMessageStream(
                user.getId(),
                request.getSessionId(),
                request.getContent(),
                request.getSourceIds()
        );
    }

    // ── GET /chat/history ─────────────────────────────────────────────────

    @GetMapping("/history")
    public ResponseEntity<?> getHistory(@RequestParam(value = "sessionId", required = false) String sessionId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);

        LOG.info("GET /chat/history user={} sessionId={}", username, sessionId);

        if (sessionId == null || sessionId.isBlank()) {
            return ResponseEntity.status(400).body(Map.of("success", false, "message", "sessionId is required"));
        }

        try {
            List<ChatMessage> history = chatService.getHistory(user.getId(), sessionId);
            if (history == null) {
                history = List.of();
            }
            return ResponseEntity.ok(history);
        } catch (com.lms.exception.ResourceNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Session not found"));
        }
    }

    // ── DELETE /chat/sessions/{id} ────────────────────────────────────────

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<?> deleteSession(@PathVariable("id") String id) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);

        LOG.info("DELETE /chat/sessions/{} user={}", id, username);

        try {
            chatService.deleteSession(user.getId(), id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Session deleted successfully"));
        } catch (com.lms.exception.ResourceNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Session not found"));
        }
    }

    // ── Request DTO ───────────────────────────────────────────────────────

    public static class ChatRequest {
        private String       sessionId;

        @NotBlank(message = "Content cannot be blank")
        private String       content;

        private List<String> sourceIds;

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public List<String> getSourceIds() { return sourceIds; }
        public void setSourceIds(List<String> sourceIds) { this.sourceIds = sourceIds; }
    }
}
