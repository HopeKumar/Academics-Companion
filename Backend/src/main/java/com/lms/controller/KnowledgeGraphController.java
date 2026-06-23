package com.lms.controller;

import com.lms.model.JobStatus;
import com.lms.model.KnowledgeGraph;
import com.lms.model.User;
import com.lms.service.AuthService;
import com.lms.service.KnowledgeGraphService;
import com.lms.service.JobStatusService;
import com.lms.service.SourceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import com.lms.config.FeaturesProperties;
import java.util.Map;

@RestController
@RequestMapping({"/knowledgegraph", "/api/v1/knowledgegraph"})
public class KnowledgeGraphController {

    private final KnowledgeGraphService knowledgeGraphService;
    private final AuthService authService;
    private final JobStatusService jobStatusService;
    private final SourceService sourceService;
    private final FeaturesProperties features;

    public KnowledgeGraphController(KnowledgeGraphService knowledgeGraphService,
                                    AuthService authService,
                                    JobStatusService jobStatusService,
                                    SourceService sourceService,
                                    FeaturesProperties features) {
        this.knowledgeGraphService = knowledgeGraphService;
        this.authService = authService;
        this.jobStatusService = jobStatusService;
        this.sourceService = sourceService;
        this.features = features;
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generateKnowledgeGraph(@RequestBody Map<String, String> payload) {
        if (!features.isKnowledgeGraphEnabled()) {
            return ResponseEntity.ok(Map.of(
                "featureEnabled", false,
                "message", "Feature temporarily disabled"
            ));
        }
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);

        String topic = payload.get("topic");
        String sourceId = payload.get("sourceId");

        if (sourceId == null || sourceId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "sourceId is required"));
        }

        try {
            sourceService.getSourceStatus(user.getId(), sourceId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }

        try {
            JobStatus job = jobStatusService.createOrUpdateJob(sourceId, "KNOWLEDGEGRAPH", "PENDING", "Queued for generation");
            knowledgeGraphService.generateKnowledgeGraphAsync(user.getId(), topic, sourceId);
            return ResponseEntity.accepted().body(job);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/source/{sourceId}")
    public ResponseEntity<?> getKnowledgeGraphBySourceId(@PathVariable("sourceId") String sourceId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);

        try {
            sourceService.getSourceStatus(user.getId(), sourceId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }

        java.util.Optional<KnowledgeGraph> kgOpt = knowledgeGraphService.getKnowledgeGraph(user.getId(), sourceId);
        if (kgOpt.isPresent()) {
            return ResponseEntity.ok(kgOpt.get());
        }

        if (!features.isKnowledgeGraphEnabled()) {
            return ResponseEntity.ok(Map.of(
                "featureEnabled", false,
                "message", "Feature temporarily disabled"
            ));
        }

        Map<String, Object> statusMap = sourceService.getSourceStatus(user.getId(), sourceId);
        String status = (String) statusMap.get("status");
        if ("PROCESSING".equals(status) || "PENDING".equals(status)) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.ACCEPTED)
                    .body(Map.of("status", "PROCESSING", "message", "Knowledge graph is still generating..."));
        }
        return ResponseEntity.status(404).body(Map.of("status", status, "error", "Knowledge graph not found. Ingestion status: " + status));
    }
}
