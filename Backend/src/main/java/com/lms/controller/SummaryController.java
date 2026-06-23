package com.lms.controller;

import com.lms.model.Summary;
import com.lms.model.User;
import com.lms.service.AuthService;
import com.lms.service.SummaryService;
import com.lms.service.AuditLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import com.lms.service.PdfExportService;
import com.lms.service.MinioStorageService;

import com.lms.model.JobStatus;
import com.lms.service.JobStatusService;

import com.lms.service.SourceService;

import java.util.Map;

@RestController
@RequestMapping({"/summary", "/summaries", "/api/v1/summary", "/api/v1/summaries"})
public class SummaryController {

    private final SummaryService summaryService;
    private final AuthService authService;
    private final PdfExportService pdfExportService;
    private final MinioStorageService minioStorageService;
    private final AuditLogService auditLogService;
    private final JobStatusService jobStatusService;
    private final SourceService sourceService;

    @Autowired
    public SummaryController(SummaryService summaryService, AuthService authService, PdfExportService pdfExportService, MinioStorageService minioStorageService, AuditLogService auditLogService, JobStatusService jobStatusService, SourceService sourceService) {
        this.summaryService = summaryService;
        this.authService = authService;
        this.pdfExportService = pdfExportService;
        this.minioStorageService = minioStorageService;
        this.auditLogService = auditLogService;
        this.jobStatusService = jobStatusService;
        this.sourceService = sourceService;
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generateSummary(@RequestBody Map<String, String> request) {
        String sourceId = request.get("sourceId");
        if (sourceId == null || sourceId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "sourceId is required"));
        }

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);

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

        JobStatus job = jobStatusService.createOrUpdateJob(sourceId, "SUMMARY", "PENDING", "Queued for generation");
        summaryService.generateSummaryAsync(user.getId(), sourceId);
        
        auditLogService.log(user.getId(), "GENERATE_SUMMARY_QUEUED", sourceId,
                "Queued summary generation for sourceId: " + sourceId, null);
                
        return ResponseEntity.accepted().body(job);
    }

    @GetMapping("/{sourceId}")
    public ResponseEntity<?> getSummary(@PathVariable("sourceId") String sourceId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);

        try {
            Summary summary = summaryService.getSummary(user.getId(), sourceId);
            return ResponseEntity.ok(summary);
        } catch (com.lms.exception.ResourceNotFoundException e) {
            Map<String, Object> statusMap = sourceService.getSourceStatus(user.getId(), sourceId);
            String status = (String) statusMap.get("status");
            if ("PROCESSING".equals(status) || "PENDING".equals(status)) {
                return ResponseEntity.status(org.springframework.http.HttpStatus.ACCEPTED)
                        .body(Map.of("status", "PROCESSING", "message", "Summary is still generating..."));
            }
            return ResponseEntity.status(404).body(Map.of("status", status, "error", "Summary not found. Ingestion status: " + status));
        }
    }

    @GetMapping("/source/{sourceId}")
    public ResponseEntity<?> getSummaryBySource(@PathVariable("sourceId") String sourceId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);

        try {
            Summary summary = summaryService.getSummary(user.getId(), sourceId);
            return ResponseEntity.ok(summary);
        } catch (com.lms.exception.ResourceNotFoundException e) {
            Map<String, Object> statusMap = sourceService.getSourceStatus(user.getId(), sourceId);
            String status = (String) statusMap.get("status");
            if ("PROCESSING".equals(status) || "PENDING".equals(status)) {
                return ResponseEntity.status(org.springframework.http.HttpStatus.ACCEPTED)
                        .body(Map.of("status", "PROCESSING", "message", "Summary is still generating..."));
            }
            return ResponseEntity.status(404).body(Map.of("status", status, "error", "Summary not found. Ingestion status: " + status));
        }
    }

    @GetMapping("/{sourceId}/download")
    public ResponseEntity<Resource> downloadSummary(@PathVariable("sourceId") String sourceId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);

        Summary summary = summaryService.getSummary(user.getId(), sourceId);
        if (summary == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            String key = pdfExportService.exportSummary(summary);
            Resource resource = minioStorageService.getFileAsResource(key);

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"");

            auditLogService.log(user.getId(), "DOWNLOAD_SUMMARY", sourceId,
                    "Downloaded summary PDF. Summary ID: " + summary.getId(), null);

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(resource.contentLength())
                    .contentType(MediaType.parseMediaType("application/pdf"))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
