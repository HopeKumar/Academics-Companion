package com.lms.controller;

import com.lms.model.SlideExport;
import com.lms.model.User;
import com.lms.repository.SlideExportRepository;
import com.lms.service.AuthService;
import com.lms.service.MinioStorageService;
import com.lms.service.SlidesGenerationService;
import com.lms.service.AuditLogService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import com.lms.config.FeaturesProperties;
import java.util.Map;

@RestController
@RequestMapping({"/slides", "/api/v1/slides"})
public class SlideExportController {

    private final SlidesGenerationService slidesGenerationService;
    private final SlideExportRepository slideExportRepository;
    private final AuthService authService;
    private final MinioStorageService minioStorageService;
    private final AuditLogService auditLogService;
    private final FeaturesProperties features;

    public SlideExportController(SlidesGenerationService slidesGenerationService,
                                 SlideExportRepository slideExportRepository,
                                 AuthService authService,
                                 MinioStorageService minioStorageService,
                                 AuditLogService auditLogService,
                                 FeaturesProperties features) {
        this.slidesGenerationService = slidesGenerationService;
        this.slideExportRepository = slideExportRepository;
        this.authService = authService;
        this.minioStorageService = minioStorageService;
        this.auditLogService = auditLogService;
        this.features = features;
    }

    @PostMapping("/export")
    public ResponseEntity<?> generateSlides(@RequestBody Map<String, String> payload) {
        if (!features.isSlidesEnabled()) {
            return ResponseEntity.ok(Map.of(
                "featureEnabled", false,
                "message", "Feature temporarily disabled"
            ));
        }
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);

        String sourceId = payload.get("sourceId");

        if (sourceId == null || sourceId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "sourceId is required"));
        }

        try {
            String exportId = slidesGenerationService.submitSlideGeneration(user.getId(), sourceId);
            auditLogService.log(user.getId(), "GENERATE_SLIDES", sourceId,
                    "Requested slide generation. Export ID: " + exportId, null);
            return ResponseEntity.accepted().body(Map.of(
                    "message", "Slide generation started in background.",
                    "id", exportId
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/export/{sourceId}")
    public ResponseEntity<?> getSlideExport(@PathVariable("sourceId") String sourceId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);

        java.util.Optional<SlideExport> exportOpt = slideExportRepository.findByUserIdAndSourceId(user.getId(), sourceId);
        if (exportOpt.isPresent()) {
            return ResponseEntity.ok(exportOpt.get());
        }

        if (!features.isSlidesEnabled()) {
            return ResponseEntity.ok(Map.of(
                "featureEnabled", false,
                "message", "Feature temporarily disabled"
            ));
        }

        return ResponseEntity.notFound().build();
    }

    @GetMapping("/source/{sourceId}")
    public ResponseEntity<?> getSlideExportBySourceId(@PathVariable("sourceId") String sourceId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);

        java.util.Optional<SlideExport> exportOpt = slideExportRepository.findByUserIdAndSourceId(user.getId(), sourceId);
        if (exportOpt.isPresent()) {
            return ResponseEntity.ok(exportOpt.get());
        }

        if (!features.isSlidesEnabled()) {
            return ResponseEntity.ok(Map.of(
                "featureEnabled", false,
                "message", "Feature temporarily disabled"
            ));
        }

        return ResponseEntity.notFound().build();
    }

    @GetMapping("/{sourceId}/download")
    public ResponseEntity<?> downloadSlides(
            @PathVariable("sourceId") String sourceId,
            @RequestParam(defaultValue = "pptx") String format) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);

        SlideExport export = slideExportRepository.findByUserIdAndSourceId(user.getId(), sourceId).orElse(null);
        if (export == null || !"COMPLETED".equals(export.getStatus())) {
            if (!features.isSlidesEnabled()) {
                return ResponseEntity.ok(Map.of(
                    "featureEnabled", false,
                    "message", "Feature temporarily disabled"
                ));
            }
            return ResponseEntity.notFound().build();
        }

        try {
            boolean isPdf = "pdf".equalsIgnoreCase(format);
            String filePathStr = isPdf ? export.getPdfFile() : export.getPptFile();
            if (filePathStr == null) {
                return ResponseEntity.notFound().build();
            }
            
            Resource resource = minioStorageService.getFileAsResource(filePathStr);
            
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"");

            MediaType mediaType = isPdf ? MediaType.APPLICATION_PDF 
                    : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.presentationml.presentation");

            auditLogService.log(user.getId(), "DOWNLOAD_SLIDES", sourceId,
                    "Downloaded slides in format: " + format, null);

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(resource.contentLength())
                    .contentType(mediaType)
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
