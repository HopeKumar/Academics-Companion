package com.lms.controller;

import com.lms.model.AIResult;
import com.lms.model.User;
import com.lms.service.AuthService;
import com.lms.service.MindmapService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import com.lms.model.JobStatus;
import com.lms.service.JobStatusService;

import com.lms.model.MindMap;
import com.lms.repository.MindmapRepository;

import java.util.List;
import java.util.Map;

import com.lms.service.SourceService;

@RestController
@RequestMapping({"/mindmaps", "/api/v1/mindmaps"})
public class MindmapController {

    private final MindmapService mindmapService;
    private final AuthService authService;
    private final MindmapRepository mindmapRepository;
    private final JobStatusService jobStatusService;
    private final SourceService sourceService;

    public MindmapController(MindmapService mindmapService, AuthService authService, MindmapRepository mindmapRepository, JobStatusService jobStatusService, SourceService sourceService) {
        this.mindmapService = mindmapService;
        this.authService = authService;
        this.mindmapRepository = mindmapRepository;
        this.jobStatusService = jobStatusService;
        this.sourceService = sourceService;
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generateMindmap(@RequestBody Map<String, String> payload) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);

        String topic = payload.get("topic");
        String sourceId = payload.get("sourceId");

        if (sourceId == null || sourceId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "sourceId is required"));
        }

        try {
            // Validate ownership synchronously before queuing the async generation
            sourceService.getSourceStatus(user.getId(), sourceId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }

        try {
            JobStatus job = jobStatusService.createOrUpdateJob(sourceId, "MINDMAP", "PENDING", "Queued for generation");
            mindmapService.generateHierarchicalMindmapAsync(user.getId(), topic, sourceId);
            return ResponseEntity.accepted().body(job);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{sourceId}")
    public ResponseEntity<?> getMindmapBySource(@PathVariable("sourceId") String sourceId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);

        try {
            // Validate ownership synchronously
            sourceService.getSourceStatus(user.getId(), sourceId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }

        java.util.Optional<com.lms.model.MindMap> mindmapOpt = mindmapRepository.findByUserIdAndSourceId(user.getId(), sourceId);
        if (mindmapOpt.isPresent()) {
            return ResponseEntity.ok(mindmapOpt.get());
        }
        Map<String, Object> statusMap = sourceService.getSourceStatus(user.getId(), sourceId);
        if ("PROCESSING".equals(statusMap.get("status"))) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.ACCEPTED)
                    .body(Map.of("status", "PROCESSING", "message", "Mindmap is still generating..."));
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/source/{sourceId}")
    public ResponseEntity<?> getMindmapBySourceId(@PathVariable("sourceId") String sourceId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);

        try {
            // Validate ownership synchronously
            sourceService.getSourceStatus(user.getId(), sourceId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }

        java.util.Optional<com.lms.model.MindMap> mindmapOpt = mindmapRepository.findByUserIdAndSourceId(user.getId(), sourceId);
        if (mindmapOpt.isPresent()) {
            return ResponseEntity.ok(mindmapOpt.get());
        }
        Map<String, Object> statusMap = sourceService.getSourceStatus(user.getId(), sourceId);
        String status = (String) statusMap.get("status");
        if ("PROCESSING".equals(status) || "PENDING".equals(status)) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.ACCEPTED)
                    .body(Map.of("status", "PROCESSING", "message", "Mindmap is still generating..."));
        }
        return ResponseEntity.status(404).body(Map.of("status", status, "error", "Mindmap not found. Ingestion status: " + status));
    }

    @GetMapping
    public ResponseEntity<?> getAllMindmaps() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);

        List<MindMap> mindmaps = mindmapRepository.findByUserIdOrderByGeneratedAtDesc(user.getId());
        return ResponseEntity.ok(mindmaps);
    }
}
