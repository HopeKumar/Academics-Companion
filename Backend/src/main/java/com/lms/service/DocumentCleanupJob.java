package com.lms.service;

import com.lms.model.Document;
import com.lms.model.DocumentStatus;
import com.lms.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DocumentCleanupJob {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentCleanupJob.class);

    private final DocumentRepository documentRepository;
    private final MinioStorageService minioStorageService;

    public DocumentCleanupJob(DocumentRepository documentRepository, MinioStorageService minioStorageService) {
        this.documentRepository = documentRepository;
        this.minioStorageService = minioStorageService;
    }

    /**
     * The ingestion pipeline writes to the "sources" collection, while the jobs in
     * this class watch the legacy "documents" collection — so sources stuck in
     * "processing" were never recovered. Field-injected and optional so the existing
     * constructor signature (used by unit tests) remains unchanged.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;

    // Runs every 10 minutes: any source still "processing" after 30 minutes is marked failed
    @Scheduled(fixedRate = 600000)
    public void failStaleProcessingSources() {
        if (mongoTemplate == null) {
            return;
        }
        java.time.Instant threshold = java.time.Instant.now().minus(30, java.time.temporal.ChronoUnit.MINUTES);
        org.springframework.data.mongodb.core.query.Query query = new org.springframework.data.mongodb.core.query.Query(
                org.springframework.data.mongodb.core.query.Criteria.where("metadata.status").in("PROCESSING", "processing")
                        .and("createdAt").lt(threshold.toString()));
        org.springframework.data.mongodb.core.query.Update update = new org.springframework.data.mongodb.core.query.Update()
                .set("metadata.status", "FAILED")
                .set("metadata.error", "Processing timed out and was marked FAILED by the recovery job");
        try {
            long modified = mongoTemplate.updateMulti(query, update, com.lms.model.Source.class).getModifiedCount();
            if (modified > 0) {
                LOG.warn("Recovery job marked {} stale 'PROCESSING' sources as FAILED", modified);
            }
        } catch (Exception e) {
            LOG.error("Stale source recovery job failed", e);
        }
    }

    // Runs every hour
    @Scheduled(fixedRate = 3600000)
    public void cleanupStaleUploadingDocuments() {
        LOG.info("Running scheduled cleanup for stale UPLOADING documents...");
        LocalDateTime threshold = LocalDateTime.now().minusHours(1);
        List<Document> staleDocuments = documentRepository.findByStatusAndCreatedAtBefore(DocumentStatus.UPLOADING, threshold);

        for (Document doc : staleDocuments) {
            LOG.info("Cleaning up stale UPLOADING document: {}", doc.getId());
            String storageKey = "documents/" + doc.getId() + "/" + doc.getOriginalFilename();
            try {
                if (minioStorageService.fileExists(storageKey)) {
                    minioStorageService.deleteFile(storageKey);
                }
                documentRepository.delete(doc);
            } catch (Exception e) {
                LOG.error("Failed to clean up stale document {}", doc.getId(), e);
            }
        }
    }

    // Runs every day
    @Scheduled(fixedRate = 86400000)
    public void hardDeleteDocuments() {
        LOG.info("Running scheduled job to hard delete soft-DELETED documents...");
        List<Document> deletedDocs = documentRepository.findByStatus(DocumentStatus.DELETED);

        for (Document doc : deletedDocs) {
            LOG.info("Hard deleting document: {}", doc.getId());
            String storageKey = "documents/" + doc.getId() + "/" + doc.getOriginalFilename();
            try {
                if (minioStorageService.fileExists(storageKey)) {
                    minioStorageService.deleteFile(storageKey);
                }
                
                // Remove artifacts (Cascaded by JPA, but need to remove from MinIO if stored there)
                doc.getArtifacts().forEach(artifact -> {
                    if (artifact.getStorageKey() != null) {
                        try {
                            if (minioStorageService.fileExists(artifact.getStorageKey())) {
                                minioStorageService.deleteFile(artifact.getStorageKey());
                            }
                        } catch (Exception ex) {
                            LOG.error("Failed to delete artifact file {}", artifact.getStorageKey(), ex);
                        }
                    }
                });

                documentRepository.delete(doc);
                LOG.info("Successfully hard deleted document {}", doc.getId());
            } catch (Exception e) {
                LOG.error("Failed to hard delete document {}", doc.getId(), e);
            }
        }
    }
}
