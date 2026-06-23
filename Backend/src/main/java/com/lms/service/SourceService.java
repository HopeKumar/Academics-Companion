package com.lms.service;

import com.lms.model.DocumentChunk;
import com.lms.model.FlashcardDeck;
import com.lms.model.Source;
import com.lms.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Service
public class SourceService {

    private static final Logger LOG = LoggerFactory.getLogger(SourceService.class);

    private final SourceRepository        sourceRepository;
    private final DocumentChunkRepository chunkRepository;
    private final IngestionPipeline        ingestionPipeline;
    private final SummaryRepository        summaryRepository;
    private final FlashcardDeckRepository  deckRepository;
    private final FlashcardRepository      flashcardRepository;
    private final MindmapRepository        mindmapRepository;
    private final PodcastRepository        podcastRepository;
    private final EmbeddingRepository      embeddingRepository;
    private final SlideExportRepository    slideExportRepository;
    private final AuditLogService          auditLogService;
    private final NotificationRepository   notificationRepository;

    @Autowired
    public SourceService(SourceRepository sourceRepository,
                          DocumentChunkRepository chunkRepository,
                          IngestionPipeline ingestionPipeline,
                          SummaryRepository summaryRepository,
                          FlashcardDeckRepository deckRepository,
                          FlashcardRepository flashcardRepository,
                          MindmapRepository mindmapRepository,
                          PodcastRepository podcastRepository,
                          EmbeddingRepository embeddingRepository,
                          SlideExportRepository slideExportRepository,
                          AuditLogService auditLogService,
                          NotificationRepository notificationRepository) {
        this.sourceRepository  = sourceRepository;
        this.chunkRepository   = chunkRepository;
        this.ingestionPipeline = ingestionPipeline;
        this.summaryRepository = summaryRepository;
        this.deckRepository    = deckRepository;
        this.flashcardRepository = flashcardRepository;
        this.mindmapRepository = mindmapRepository;
        this.podcastRepository = podcastRepository;
        this.embeddingRepository = embeddingRepository;
        this.slideExportRepository = slideExportRepository;
        this.auditLogService = auditLogService;
        this.notificationRepository = notificationRepository;
    }

    public List<Source> getSourcesForUser(String userId) {
        return sourceRepository.findByUserId(userId);
    }

    public Map<String, Object> getSourceStatus(String userId, String sourceId) {
        try {
            LOG.info("Source lookup: fetching sourceId={} for userId={}", sourceId, userId);
            Source source = sourceRepository.findById(sourceId).orElse(null);
            
            if (source == null) {
                LOG.error("Source lookup failed: Source {} not found", sourceId);
                throw new IllegalArgumentException("Source not found");
            }

            if (!source.getUserId().equals(userId)) {
                LOG.error("Source lookup failed: Unauthorized access by userId={}", userId);
                throw new SecurityException("Unauthorized to access this source");
            }

            LOG.info("Metadata lookup: extracting metadata for sourceId={}", sourceId);
            Map<String, Object> metadata = source.getMetadata();
            String status = "PROCESSING";
            if (metadata != null && metadata.containsKey("status")) {
                status = (String) metadata.get("status");
                LOG.info("Processing status lookup: found status='{}' for sourceId={}", status, sourceId);
            } else {
                LOG.warn("Processing status lookup: 'status' field missing in metadata for sourceId={}, defaulting to PROCESSING", sourceId);
            }

            int progress = 0;
            if ("READY".equalsIgnoreCase(status) || "COMPLETED".equalsIgnoreCase(status) || "COMPLETE".equalsIgnoreCase(status)) {
                progress = 100;
                status = "COMPLETED";
            } else if ("FAILED".equalsIgnoreCase(status) || "FAIL".equalsIgnoreCase(status)) {
                progress = 0;
                status = "FAILED";
            } else {
                status = "PROCESSING";
                progress = 50;
            }

            return Map.of(
                "id", source.getId(),
                "status", status,
                "progress", progress,
                "metadata", metadata != null ? metadata : Map.of(),
                "message", progress == 100 ? "Processing complete" : "Document is being processed"
            );
        } catch (IllegalArgumentException | SecurityException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("Exception during getSourceStatus for sourceId={}: {}", sourceId, e.getMessage(), e);
            return Map.of(
                "id", sourceId,
                "status", "PROCESSING",
                "progress", 0,
                "message", "Status lookup temporarily unavailable"
            );
        }
    }

    public void deleteSource(String userId, String sourceId) {
        Source source = sourceRepository.findById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Source not found"));

        if (!source.getUserId().equals(userId)) {
            throw new SecurityException("Unauthorized to delete this source");
        }

        // Cascade delete all generated artifacts for this source
        LOG.info("Cascade deleting all artifacts for sourceId={}", sourceId);
        auditLogService.log(userId, "DELETE_SOURCE", sourceId,
                "Deleted source: " + source.getName(), null);

        // 0. Delete notifications
        try {
            notificationRepository.deleteByResourceId(sourceId);
        } catch (Exception e) {
            LOG.warn("Failed to delete notifications for sourceId={}: {}", sourceId, e.getMessage());
        }

        // 1. Delete document chunks (embeddings/RAG data)
        chunkRepository.deleteBySourceId(sourceId);

        // 2. Delete summaries
        try {
            summaryRepository.deleteBySourceId(sourceId);
        } catch (Exception e) {
            LOG.warn("Failed to delete summaries for sourceId={}: {}", sourceId, e.getMessage());
        }

        // 3. Delete flashcard decks and their cards
        try {
            List<FlashcardDeck> decks = deckRepository.findBySourceId(sourceId);
            for (FlashcardDeck deck : decks) {
                flashcardRepository.deleteByDeckId(deck.getId());
            }
            deckRepository.deleteBySourceId(sourceId);
        } catch (Exception e) {
            LOG.warn("Failed to delete flashcards for sourceId={}: {}", sourceId, e.getMessage());
        }

        // 4. Delete mind maps
        try {
            mindmapRepository.deleteBySourceId(sourceId);
        } catch (Exception e) {
            LOG.warn("Failed to delete mindmaps for sourceId={}: {}", sourceId, e.getMessage());
        }

        // 5. Delete podcasts
        try {
            podcastRepository.deleteBySourceId(sourceId);
        } catch (Exception e) {
            LOG.warn("Failed to delete podcasts for sourceId={}: {}", sourceId, e.getMessage());
        }

        // 6. Delete slide exports
        try {
            slideExportRepository.deleteBySourceId(sourceId);
        } catch (Exception e) {
            LOG.warn("Failed to delete slide exports for sourceId={}: {}", sourceId, e.getMessage());
        }

        // 7. Delete embeddings
        try {
            embeddingRepository.deleteBySourceId(sourceId);
        } catch (Exception e) {
            LOG.warn("Failed to delete embeddings for sourceId={}: {}", sourceId, e.getMessage());
        }

        // 8. Delete the source record itself
        sourceRepository.deleteById(sourceId);
        LOG.info("Successfully deleted source {} and all its artifacts", sourceId);
    }

    public Source ingestDocument(String userId, InputStream inputStream,
                                                    String filename, String contentType, long fileSize) {
        // Prevent duplicate ingestion — but only when the previous ingestion
        // actually completed. Returning a source stuck in "processing" or
        // "failed" made it impossible to ever reprocess a document by
        // re-uploading it (permanent PROCESSING trap).
        List<Source> existingSources = sourceRepository.findByUserId(userId);
        for (Source s : existingSources) {
            if (s.getName().equals(filename) && s.getFileSize() == fileSize) {
                String existingStatus = String.valueOf(
                        s.getMetadata() != null ? s.getMetadata().getOrDefault("status", "") : "");
                if ("complete".equalsIgnoreCase(existingStatus)
                        || "completed".equalsIgnoreCase(existingStatus)
                        || "ready".equalsIgnoreCase(existingStatus)) {
                    return s; // Genuinely done — reuse the existing source
                }
                // Stuck or failed — reset and reprocess instead of returning a zombie record
                LOG.warn("Re-ingesting source {} previously stuck in status '{}'", s.getId(), existingStatus);
                if (s.getMetadata() == null) {
                    s.setMetadata(new java.util.HashMap<>());
                }
                s.getMetadata().put("status", "PROCESSING");
                s.getMetadata().put("progress", 10);
                s.getMetadata().remove("error");
                Source reset = sourceRepository.save(s);
                ingestionPipeline.ingest(reset, inputStream, filename, contentType);
                return reset;
            }
        }
        
        // Save initial source record with processing status
        Source source = new Source(userId, filename, determineType(filename, contentType), fileSize);
        source.getMetadata().put("status", "PROCESSING");
        source.getMetadata().put("progress", 10);
        Source savedSource = sourceRepository.save(source);
        LOG.info("SOURCE CREATED");

        // Start async processing
        ingestionPipeline.ingest(savedSource, inputStream, filename, contentType);
        return savedSource;
    }

    /**
     * Async Ingestion of URL resource link, routing via IngestionPipeline.
     */
    public Source ingestUrl(String userId, String urlString) {
        // Prevent duplicate URL ingestion
        List<Source> existingSources = sourceRepository.findByUserId(userId);
        for (Source s : existingSources) {
            if (urlString.equals(s.getUrl())) {
                return s; // Return existing instead of throwing
            }
        }

        String type = urlString.contains("youtube.com") || urlString.contains("youtu.be") ? "YOUTUBE" : "URL";
        String name = extractDomain(urlString);
        
        Source source = new Source(userId, name, type, 0);
        source.setUrl(urlString);
        source.getMetadata().put("status", "PROCESSING");
        source.getMetadata().put("progress", 10);
        Source savedSource = sourceRepository.save(source);
        LOG.info("SOURCE CREATED");

        // Start async processing
        ingestionPipeline.ingestUrl(savedSource, urlString);
        return savedSource;
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private String determineType(String filename, String contentType) {
        if (filename.toLowerCase().endsWith(".pdf") || "application/pdf".equals(contentType)) return "PDF";
        if (filename.toLowerCase().endsWith(".docx")) return "DOCX";
        return "TXT";
    }

    private String extractDomain(String urlString) {
        try {
            java.net.URI uri = java.net.URI.create(urlString);
            String host = uri.getHost();
            return host != null ? host : urlString;
        } catch (Exception e) {
            return urlString;
        }
    }
}
