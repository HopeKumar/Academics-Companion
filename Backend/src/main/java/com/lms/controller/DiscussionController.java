package com.lms.controller;

import com.lms.model.DiscussionReply;
import com.lms.model.DiscussionThread;
import com.lms.service.AuthService;
import com.lms.model.User;
import com.lms.service.DiscussionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/discussions", "/api/v1/discussions"})
public class DiscussionController {

    private final DiscussionService discussionService;
    private final AuthService authService;

    @Autowired
    public DiscussionController(DiscussionService discussionService, AuthService authService) {
        this.discussionService = discussionService;
        this.authService = authService;
    }

    private String getCurrentUserId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);
        return user.getId();
    }

    @PostMapping
    public ResponseEntity<?> createThread(@RequestBody Map<String, String> payload) {
        String title = payload.get("title");
        String content = payload.getOrDefault("content", "");
        String sourceId = payload.get("sourceId");
        
        if (title == null || title.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "title is required"));
        }
        
        return ResponseEntity.ok(discussionService.createThread(title, content, getCurrentUserId(), sourceId));
    }

    @GetMapping
    public ResponseEntity<List<DiscussionThread>> getAllThreads() {
        return ResponseEntity.ok(discussionService.getAllThreads());
    }

    @PostMapping("/reply")
    public ResponseEntity<DiscussionReply> createReply(@RequestBody Map<String, String> payload) {
        String threadId = payload.get("threadId");
        String content = payload.get("content");
        return ResponseEntity.ok(discussionService.createReply(threadId, content, getCurrentUserId()));
    }

    @PostMapping("/upvote")
    public ResponseEntity<?> upvote(@RequestBody Map<String, String> payload) {
        if (payload.containsKey("threadId")) {
            return ResponseEntity.ok(discussionService.upvoteThread(payload.get("threadId")));
        } else if (payload.containsKey("replyId")) {
            return ResponseEntity.ok(discussionService.upvoteReply(payload.get("replyId")));
        }
        return ResponseEntity.badRequest().body(Map.of("error", "Provide threadId or replyId"));
    }

    @PostMapping("/accepted")
    public ResponseEntity<DiscussionReply> markAccepted(@RequestBody Map<String, String> payload) {
        String replyId = payload.get("replyId");
        return ResponseEntity.ok(discussionService.markAcceptedAnswer(replyId));
    }
    
    @PostMapping("/summary")
    public ResponseEntity<DiscussionThread> generateSummary(@RequestBody Map<String, String> payload) {
        String threadId = payload.get("threadId");
        return ResponseEntity.ok(discussionService.generateAISummary(threadId));
    }
}
