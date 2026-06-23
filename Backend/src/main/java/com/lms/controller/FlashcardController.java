package com.lms.controller;

import com.lms.model.AIResult;
import com.lms.model.Flashcard;
import com.lms.model.FlashcardDeck;
import com.lms.model.User;
import com.lms.service.AuthService;
import com.lms.service.FlashcardService;
import com.lms.service.PdfExportService;
import com.lms.service.MinioStorageService;
import com.lms.service.AuditLogService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.lms.model.JobStatus;
import com.lms.service.JobStatusService;

import java.util.List;
import java.util.Map;

import com.lms.service.SourceService;

@RestController
@RequestMapping({"/flashcards", "/api/v1/flashcards"})
public class FlashcardController {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(FlashcardController.class);

    private final FlashcardService flashcardService;
    private final AuthService authService;
    private final PdfExportService pdfExportService;
    private final MinioStorageService minioStorageService;
    private final AuditLogService auditLogService;
    private final JobStatusService jobStatusService;
    private final SourceService sourceService;

    public FlashcardController(FlashcardService flashcardService, AuthService authService, PdfExportService pdfExportService, MinioStorageService minioStorageService, AuditLogService auditLogService, JobStatusService jobStatusService, SourceService sourceService) {
        this.flashcardService = flashcardService;
        this.authService = authService;
        this.pdfExportService = pdfExportService;
        this.minioStorageService = minioStorageService;
        this.auditLogService = auditLogService;
        this.jobStatusService = jobStatusService;
        this.sourceService = sourceService;
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generateFlashcards(@RequestBody Map<String, String> payload) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);

        String topic    = payload.get("topic");
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
            JobStatus job = jobStatusService.createOrUpdateJob(sourceId, "FLASHCARDS", "PENDING", "Queued for generation");
            flashcardService.generateDeckAsync(user.getId(), topic, sourceId);
            
            auditLogService.log(user.getId(), "GENERATE_FLASHCARDS_QUEUED", sourceId,
                    "Queued flashcard deck generation", null);
                    
            return ResponseEntity.accepted().body(job);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/source/{sourceId}")
    public ResponseEntity<?> getCardsBySource(@PathVariable String sourceId) {
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

        FlashcardDeck deck = flashcardService.getDecksForUser(user.getId()).stream()
                .filter(d -> sourceId.equals(d.getSourceId()))
                .findFirst()
                .orElse(null);

        if (deck == null) {
            Map<String, Object> statusMap = sourceService.getSourceStatus(user.getId(), sourceId);
            String status = (String) statusMap.get("status");
            if ("PROCESSING".equals(status) || "PENDING".equals(status)) {
                return ResponseEntity.status(org.springframework.http.HttpStatus.ACCEPTED)
                        .body(Map.of("status", "PROCESSING", "message", "Flashcards are still generating..."));
            }
            return ResponseEntity.status(404).body(Map.of("status", status, "error", "Flashcard deck not found. Ingestion status: " + status));
        }

        List<Flashcard> flashcards = flashcardService.getCardsForDeck(deck.getId());
        if (flashcards.isEmpty()) {
            Flashcard fallback = new Flashcard(deck.getId(), "Flashcards currently unavailable", "Please try regenerating flashcards for this document.", "General");
            return ResponseEntity.ok(List.of(fallback));
        }
        
        return ResponseEntity.ok(flashcards);
    }

    /** GET /flashcards/due — all cards due for review today for the current user */
    @GetMapping("/due")
    public ResponseEntity<List<Flashcard>> getDueCards() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);
        return ResponseEntity.ok(flashcardService.getDueCards(user.getId()));
    }

    @GetMapping("/decks")
    public ResponseEntity<List<FlashcardDeck>> getDecks() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);
        return ResponseEntity.ok(flashcardService.getDecksForUser(user.getId()));
    }

    @GetMapping("/decks/{deckId}")
    public ResponseEntity<?> getCards(@PathVariable String deckId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);

        FlashcardDeck deck = flashcardService.getDecksForUser(user.getId()).stream()
                .filter(d -> d.getId().equals(deckId))
                .findFirst()
                .orElse(null);

        if (deck == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(flashcardService.getCardsForDeck(deckId));
    }

    /** GET /flashcards/decks/{deckId}/stats — mature/young/new/due card breakdown */
    @GetMapping("/decks/{deckId}/stats")
    public ResponseEntity<?> getDeckStats(@PathVariable String deckId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);

        FlashcardDeck deck = flashcardService.getDecksForUser(user.getId()).stream()
                .filter(d -> d.getId().equals(deckId))
                .findFirst()
                .orElse(null);

        if (deck == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(flashcardService.getDeckStats(deckId));
    }

    @PostMapping("/review")
    public ResponseEntity<?> reviewCard(@RequestBody Map<String, Object> payload) {
        String cardId = (String) payload.get("cardId");
        if (cardId == null || cardId.isBlank()) {
            throw new IllegalArgumentException("cardId is required");
        }
        
        Object qualityObj = payload.get("quality");
        if (qualityObj == null) {
            throw new IllegalArgumentException("quality is required");
        }
        
        int quality;
        try {
            quality = Integer.parseInt(qualityObj.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("quality must be an integer");
        }
        
        if (quality < 0 || quality > 5) {
            throw new IllegalArgumentException("quality must be between 0 and 5");
        }

        LOG.info("POST /flashcards/review cardId={} quality={}", cardId, quality);
        
        Flashcard updated = flashcardService.reviewFlashcard(cardId, quality);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/decks/{deckId}/download")
    public ResponseEntity<Resource> downloadFlashcards(@PathVariable String deckId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);

        FlashcardDeck deck = flashcardService.getDecksForUser(user.getId()).stream()
                .filter(d -> d.getId().equals(deckId))
                .findFirst()
                .orElse(null);

        if (deck == null) {
            return ResponseEntity.notFound().build();
        }

        List<Flashcard> flashcards = flashcardService.getCardsForDeck(deckId);

        try {
            String key = pdfExportService.exportFlashcards(deck, flashcards);
            Resource resource = minioStorageService.getFileAsResource(key);

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"");

            auditLogService.log(user.getId(), "DOWNLOAD_FLASHCARDS", deck.getSourceId(),
                    "Downloaded flashcard deck PDF. Deck ID: " + deckId, null);

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(resource.contentLength())
                    .contentType(MediaType.parseMediaType("application/pdf"))
                    .body(resource);
        } catch (Exception e) {
            LOG.error("Failed to generate/download flashcard PDF", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
