package com.lms.controller;

import com.lms.model.User;
import com.lms.service.AuthService;
import com.lms.service.MinioStorageService;
import com.lms.service.PodcastService;
import com.lms.service.AuditLogService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import com.lms.model.Podcast;
import com.lms.repository.PodcastRepository;

import java.util.List;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;

import com.lms.service.SourceService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping({"/podcasts", "/api/v1/podcasts"})
public class PodcastController {

    private static final Logger LOG = LoggerFactory.getLogger(PodcastController.class);

    private final PodcastService podcastService;
    private final AuthService authService;
    private final PodcastRepository podcastRepository;
    private final MinioStorageService minioStorageService;
    private final AuditLogService auditLogService;
    private final SourceService sourceService;

    public PodcastController(PodcastService podcastService, AuthService authService, PodcastRepository podcastRepository, MinioStorageService minioStorageService, AuditLogService auditLogService, SourceService sourceService) {
        this.podcastService = podcastService;
        this.authService = authService;
        this.podcastRepository = podcastRepository;
        this.minioStorageService = minioStorageService;
        this.auditLogService = auditLogService;
        this.sourceService = sourceService;
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generatePodcast(@RequestBody Map<String, String> payload) {
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
            String podcastId = podcastService.submitPodcastGeneration(user.getId(), topic, sourceId);
            auditLogService.log(user.getId(), "GENERATE_PODCAST", sourceId,
                    "Requested podcast generation. Podcast ID: " + podcastId, null);
            return ResponseEntity.accepted().body(Map.of(
                "message", "Podcast generation started in background.",
                "id", podcastId
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/status/{id}")
    public ResponseEntity<?> getPodcastStatus(@PathVariable("id") String id) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);

        return podcastRepository.findById(id)
                .filter(p -> p.getUserId().equals(user.getId()))
                .map(p -> ResponseEntity.ok(Map.of(
                        "podcastId", p.getId(),
                        "status", p.getAudioStatus() != null ? p.getAudioStatus() : p.getStatus(),
                        "audioReady", p.getAudioStatus() == com.lms.model.PodcastJobStatus.COMPLETED
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/result/{id}")
    public ResponseEntity<?> getPodcastResult(@PathVariable("id") String id) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);

        return podcastRepository.findById(id)
                .filter(p -> p.getUserId().equals(user.getId()))
                .map(p -> {
                    if (p.getStatus() == com.lms.model.PodcastJobStatus.COMPLETED) {
                        return ResponseEntity.ok(p.getScript());
                    } else if (p.getStatus() == com.lms.model.PodcastJobStatus.FAILED) {
                        return ResponseEntity.badRequest().body(Map.of("error", "Podcast generation failed."));
                    } else {
                        return ResponseEntity.accepted().body(Map.of("status", p.getStatus()));
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<?> getPodcastHistory() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);
        return ResponseEntity.ok(podcastRepository.findByUserIdOrderByCreatedAtDesc(user.getId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getPodcastDetails(@PathVariable("id") String id) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);

        return podcastRepository.findById(id)
                .filter(p -> p.getUserId().equals(user.getId()))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/source/{sourceId}")
    public ResponseEntity<?> getPodcastBySourceId(@PathVariable("sourceId") String sourceId) {
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

        java.util.Optional<com.lms.model.Podcast> podcastOpt = podcastRepository.findByUserIdAndSourceId(user.getId(), sourceId);
        if (podcastOpt.isPresent()) {
            return ResponseEntity.ok(podcastOpt.get());
        }
        Map<String, Object> statusMap = sourceService.getSourceStatus(user.getId(), sourceId);
        if ("PROCESSING".equals(statusMap.get("status"))) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.ACCEPTED)
                    .body(Map.of("status", "PROCESSING", "message", "Podcast is still generating..."));
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/{id}/audio")
    public ResponseEntity<Resource> getPodcastAudio(@PathVariable("id") String id) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);

        Podcast podcast = podcastRepository.findById(id).orElse(null);

        String podcastId = id;
        String audioKey = podcast != null ? podcast.getAudioFile() : "null";
        String audioUrl = podcast != null ? podcast.getAudioUrl() : "null";
        String storageLookupResult = "N/A";
        String endpointResult = "404 Not Found";

        if (podcast == null) {
            storageLookupResult = "Podcast object not found in repository";
            endpointResult = "404 Not Found (Podcast Null)";
            LOG.info("[AUDIO_DEBUG]\npodcastId: {}\naudioKey: {}\naudioUrl: {}\nstorageLookupResult: {}\nendpointResult: {}",
                    podcastId, audioKey, audioUrl, storageLookupResult, endpointResult);
            return ResponseEntity.notFound().build();
        }

        if (!podcast.getUserId().equals(user.getId())) {
            storageLookupResult = "User ID mismatch (podcast owner: " + podcast.getUserId() + ", authenticated: " + user.getId() + ")";
            endpointResult = "404 Not Found (User Mismatch)";
            LOG.info("[AUDIO_DEBUG]\npodcastId: {}\naudioKey: {}\naudioUrl: {}\nstorageLookupResult: {}\nendpointResult: {}",
                    podcastId, audioKey, audioUrl, storageLookupResult, endpointResult);
            return ResponseEntity.notFound().build();
        }

        if (podcast.getAudioFile() == null) {
            storageLookupResult = "audioFile field is null in Podcast record";
            endpointResult = "404 Not Found (Audio File Key Null)";
            LOG.info("[AUDIO_DEBUG]\npodcastId: {}\naudioKey: {}\naudioUrl: {}\nstorageLookupResult: {}\nendpointResult: {}",
                    podcastId, audioKey, audioUrl, storageLookupResult, endpointResult);
            return ResponseEntity.notFound().build();
        }

        try {
            String key = podcast.getAudioFile();
            Resource resource = minioStorageService.getFileAsResource(key);

            if (resource == null) {
                storageLookupResult = "minioStorageService returned null resource";
                endpointResult = "404 Not Found (Resource Null)";
                LOG.info("[AUDIO_DEBUG]\npodcastId: {}\naudioKey: {}\naudioUrl: {}\nstorageLookupResult: {}\nendpointResult: {}",
                        podcastId, audioKey, audioUrl, storageLookupResult, endpointResult);
                return ResponseEntity.notFound().build();
            }

            if (!resource.exists()) {
                storageLookupResult = "Resource does not exist (key: " + key + ")";
                endpointResult = "404 Not Found (Resource Does Not Exist)";
                LOG.info("[AUDIO_DEBUG]\npodcastId: {}\naudioKey: {}\naudioUrl: {}\nstorageLookupResult: {}\nendpointResult: {}",
                        podcastId, audioKey, audioUrl, storageLookupResult, endpointResult);
                return ResponseEntity.notFound().build();
            }

            if (!resource.isReadable()) {
                storageLookupResult = "Resource is not readable (key: " + key + ")";
                endpointResult = "404 Not Found (Resource Not Readable)";
                LOG.info("[AUDIO_DEBUG]\npodcastId: {}\naudioKey: {}\naudioUrl: {}\nstorageLookupResult: {}\nendpointResult: {}",
                        podcastId, audioKey, audioUrl, storageLookupResult, endpointResult);
                return ResponseEntity.notFound().build();
            }

            storageLookupResult = "File exists and is readable (size: " + resource.contentLength() + " bytes)";
            endpointResult = "200 OK";

            LOG.info("[AUDIO_DEBUG]\npodcastId: {}\naudioKey: {}\naudioUrl: {}\nstorageLookupResult: {}\nendpointResult: {}",
                    podcastId, audioKey, audioUrl, storageLookupResult, endpointResult);

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"");

            auditLogService.log(user.getId(), "DOWNLOAD_PODCAST", podcast.getSourceId(),
                    "Downloaded podcast audio. Podcast ID: " + id, null);

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(resource.contentLength())
                    .contentType(MediaType.parseMediaType("audio/wav"))
                    .body(resource);
        } catch (Exception e) {
            storageLookupResult = "Exception during storage lookup/retrieval: " + e.getMessage();
            endpointResult = "404 Not Found (Catch Exception)";
            LOG.info("[AUDIO_DEBUG]\npodcastId: {}\naudioKey: {}\naudioUrl: {}\nstorageLookupResult: {}\nendpointResult: {}",
                    podcastId, audioKey, audioUrl, storageLookupResult, endpointResult);
            return ResponseEntity.notFound().build();
        }
    }
}
