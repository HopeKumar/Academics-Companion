package com.lms.controller;

import com.lms.model.Source;
import com.lms.model.User;
import com.lms.service.AuthService;
import com.lms.service.SourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/sources", "/api/v1/sources"})
public class UploadController {

    private static final Logger LOG = LoggerFactory.getLogger(UploadController.class);

    private final SourceService sourceService;
    private final AuthService   authService;
    private final com.lms.metrics.MetricsRegistry metricsRegistry;

    @Autowired
    public UploadController(SourceService sourceService, AuthService authService, com.lms.metrics.MetricsRegistry metricsRegistry) {
        this.sourceService = sourceService;
        this.authService   = authService;
        this.metricsRegistry = metricsRegistry;
    }

    // ── GET /sources ──────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<Source>> getSources() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);

        LOG.info("GET /sources user={}", username);
        List<Source> sources = sourceService.getSourcesForUser(user.getId());
        return ResponseEntity.ok(sources);
    }

    // ── GET /sources/{id} ─────────────────────────────────────────────────

    @GetMapping("/{id}")
    public ResponseEntity<?> getSourceStatus(@PathVariable("id") String id) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);

        try {
            Map<String, Object> status = sourceService.getSourceStatus(user.getId(), id);
            return ResponseEntity.ok(status);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            LOG.error("Failed to fetch source status for {}: {}", id, e.getMessage());
            return ResponseEntity.ok(Map.of(
                "id", id,
                "status", "PROCESSING",
                "progress", 0,
                "message", "Status lookup temporarily unavailable"
            ));
        }
    }

    // ── POST /sources/upload ──────────────────────────────────────────────

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Empty file provided"));
        }

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);

        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document.txt";
        LOG.info("POST /sources/upload user={} file={} size={}", username, filename, file.getSize());
        LOG.info("STEP 1 Upload Started");

        long startTime = System.currentTimeMillis();
        java.nio.file.Path tempFile = null;
        try {
            tempFile = java.nio.file.Files.createTempFile("upload-", ".tmp");
            try (java.io.InputStream is = file.getInputStream()) {
                java.nio.file.Files.copy(is, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            
            com.lms.util.DeletingFileInputStream deletingStream = new com.lms.util.DeletingFileInputStream(tempFile.toFile());
            
            Source source = sourceService.ingestDocument(
                    user.getId(),
                    deletingStream,
                    filename,
                    file.getContentType(),
                    file.getSize()
            );
            metricsRegistry.recordUpload(System.currentTimeMillis() - startTime);
            return ResponseEntity.accepted().body(source);
        } catch (Exception e) {
            if (tempFile != null) {
                try {
                    java.nio.file.Files.deleteIfExists(tempFile);
                } catch (Exception ex) {
                    LOG.warn("Failed to delete temp file on upload failure: {}", tempFile, ex);
                }
            }
            LOG.error("Failed to start upload for file {}: {}", filename, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to start document processing: " + e.getMessage()));
        }
    }

    // ── POST /sources/url ─────────────────────────────────────────────────

    @PostMapping("/url")
    public ResponseEntity<?> uploadUrl(@RequestBody Map<String, String> request) {
        String urlString = request.get("url");
        if (urlString == null || urlString.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "URL parameter is required"));
        }

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);

        LOG.info("POST /sources/url user={} url={}", username, urlString);

        long startTime = System.currentTimeMillis();
        try {
            Source source = sourceService.ingestUrl(user.getId(), urlString);
            metricsRegistry.recordUpload(System.currentTimeMillis() - startTime);
            return ResponseEntity.accepted().body(source);
        } catch (Exception e) {
            LOG.error("Failed to start scraping for URL {}: {}", urlString, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to start URL processing: " + e.getMessage()));
        }
    }

    // ── DELETE /sources/{id} ──────────────────────────────────────────────

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteSource(@PathVariable("id") String id) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);

        LOG.info("DELETE /sources/{} user={}", id, username);

        try {
            sourceService.deleteSource(user.getId(), id);
            return ResponseEntity.ok(Map.of("message", "Source deleted successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to delete source: " + e.getMessage()));
        }
    }
}
