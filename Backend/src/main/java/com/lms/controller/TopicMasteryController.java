package com.lms.controller;

import com.lms.model.TopicMastery;
import com.lms.service.TopicMasteryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/mastery", "/api/v1/mastery"})
public class TopicMasteryController {

    private final TopicMasteryService topicMasteryService;
    private final com.lms.service.AuthService authService;

    @Autowired
    public TopicMasteryController(TopicMasteryService topicMasteryService, com.lms.service.AuthService authService) {
        this.topicMasteryService = topicMasteryService;
        this.authService = authService;
    }

    private String resolveStudentId(String studentId) {
        if ("current-user-id".equals(studentId)) {
            String username = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
            return authService.getUserByUsername(username).getId();
        }
        return studentId;
    }

    @GetMapping("/student/{id}")
    public ResponseEntity<List<TopicMastery>> getStudentMastery(@PathVariable String id) {
        return ResponseEntity.ok(topicMasteryService.getAllMastery(resolveStudentId(id)));
    }

    @GetMapping("/subject/{subject}")
    public ResponseEntity<List<TopicMastery>> getSubjectMastery(@PathVariable String subject) {
        return ResponseEntity.ok(topicMasteryService.getMasteryBySubject(subject));
    }

    @GetMapping("/weak-topics/{studentId}")
    public ResponseEntity<List<TopicMastery>> getWeakTopics(@PathVariable String studentId) {
        return ResponseEntity.ok(topicMasteryService.getWeakTopics(resolveStudentId(studentId)));
    }

    @GetMapping("/strong-topics/{studentId}")
    public ResponseEntity<List<TopicMastery>> getStrongTopics(@PathVariable String studentId) {
        return ResponseEntity.ok(topicMasteryService.getStrongTopics(resolveStudentId(studentId)));
    }
}
