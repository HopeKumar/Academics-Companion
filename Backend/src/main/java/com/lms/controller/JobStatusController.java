package com.lms.controller;

import com.lms.model.JobStatus;
import com.lms.model.User;
import com.lms.service.AuthService;
import com.lms.service.JobStatusService;
import com.lms.service.SourceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping({"/api/v1/jobs", "/jobs"})
public class JobStatusController {

    private final JobStatusService jobStatusService;
    private final AuthService authService;
    private final SourceService sourceService;

    public JobStatusController(JobStatusService jobStatusService, AuthService authService, SourceService sourceService) {
        this.jobStatusService = jobStatusService;
        this.authService = authService;
        this.sourceService = sourceService;
    }

    @GetMapping("/source/{sourceId}")
    public ResponseEntity<?> getJobsForSource(@PathVariable("sourceId") String sourceId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);

        try {
            // Validate ownership
            sourceService.getSourceStatus(user.getId(), sourceId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error: " + e.getMessage()));
        }

        List<JobStatus> jobs = jobStatusService.getJobsForSource(sourceId);
        return ResponseEntity.ok(jobs);
    }

    @GetMapping("/source/{sourceId}/{type}")
    public ResponseEntity<?> getJobStatus(@PathVariable("sourceId") String sourceId, @PathVariable("type") String type) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);

        try {
            // Validate ownership
            sourceService.getSourceStatus(user.getId(), sourceId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error: " + e.getMessage()));
        }

        Optional<JobStatus> job = jobStatusService.getJobStatus(sourceId, type.toUpperCase());
        if (job.isPresent()) {
            return ResponseEntity.ok(job.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
