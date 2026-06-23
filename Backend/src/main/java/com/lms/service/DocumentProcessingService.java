package com.lms.service;

import com.lms.model.ArtifactType;
import com.lms.model.Document;
import com.lms.model.DocumentArtifact;
import com.lms.model.DocumentStatus;
import com.lms.repository.DocumentArtifactRepository;
import com.lms.repository.DocumentRepository;
import com.lms.repository.EmbeddingRepository;
import com.lms.repository.SummaryRepository;
import com.lms.repository.FlashcardDeckRepository;
import com.lms.repository.FlashcardRepository;
import com.lms.repository.QuestionRepository;
import com.lms.repository.MindmapRepository;
import com.lms.repository.NotificationRepository;
import com.lms.service.ai.EmbeddingService;
import com.lms.config.FeaturesProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import java.io.InputStream;
import java.util.UUID;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@Service
public class DocumentProcessingService {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentProcessingService.class);

    private final DocumentRepository documentRepository;
    private final DocumentArtifactRepository documentArtifactRepository;
    private final MinioStorageService minioStorageService;
    private final PdfExtractionService pdfExtractionService;
    private final EmbeddingService embeddingService;
    private final SummaryService summaryService;
    private final FlashcardService flashcardService;
    private final QuestionGenerationService questionGenerationService;
    private final MindmapService mindmapService;
    private final Executor aiTaskExecutor;
    private final EmbeddingRepository embeddingRepository;
    private final SummaryRepository summaryRepository;
    private final FlashcardDeckRepository deckRepository;
    private final FlashcardRepository flashcardRepository;
    private final QuestionRepository questionRepository;
    private final MindmapRepository mindmapRepository;
    private final MongoTemplate mongoTemplate;
    private final NotificationRepository notificationRepository;
    private final FeaturesProperties features;

    public DocumentProcessingService(DocumentRepository documentRepository, 
                                     DocumentArtifactRepository documentArtifactRepository,
                                     MinioStorageService minioStorageService,
                                     PdfExtractionService pdfExtractionService,
                                     EmbeddingService embeddingService,
                                     SummaryService summaryService,
                                     FlashcardService flashcardService,
                                     QuestionGenerationService questionGenerationService,
                                     MindmapService mindmapService,
                                     EmbeddingRepository embeddingRepository,
                                     SummaryRepository summaryRepository,
                                     FlashcardDeckRepository deckRepository,
                                     FlashcardRepository flashcardRepository,
                                     QuestionRepository questionRepository,
                                     MindmapRepository mindmapRepository,
                                     MongoTemplate mongoTemplate,
                                     NotificationRepository notificationRepository,
                                     FeaturesProperties features,
                                     @org.springframework.beans.factory.annotation.Qualifier("aiTaskExecutor") Executor aiTaskExecutor) {
        this.documentRepository = documentRepository;
        this.documentArtifactRepository = documentArtifactRepository;
        this.minioStorageService = minioStorageService;
        this.pdfExtractionService = pdfExtractionService;
        this.embeddingService = embeddingService;
        this.summaryService = summaryService;
        this.flashcardService = flashcardService;
        this.questionGenerationService = questionGenerationService;
        this.mindmapService = mindmapService;
        this.embeddingRepository = embeddingRepository;
        this.summaryRepository = summaryRepository;
        this.deckRepository = deckRepository;
        this.flashcardRepository = flashcardRepository;
        this.questionRepository = questionRepository;
        this.mindmapRepository = mindmapRepository;
        this.mongoTemplate = mongoTemplate;
        this.notificationRepository = notificationRepository;
        this.features = features;
        this.aiTaskExecutor = aiTaskExecutor;
    }

    @Async("aiTaskExecutor")
    @Transactional
    public void processDocument(String documentId) {
        LOG.info("Starting processing for document {}", documentId);
        
        Document document = documentRepository.findById(documentId).orElse(null);
        if (document == null) {
            LOG.error("Document not found for processing: {}", documentId);
            return;
        }

        try {
            document.setStatus(DocumentStatus.PROCESSING);
            document.setEmbeddingStatus(com.lms.model.ProcessingStatus.PROCESSING);
            documentRepository.save(document);

            // 1. Download file from MinIO
            String storageKey = "documents/" + document.getId() + "/" + document.getOriginalFilename();
            InputStream fileStream = minioStorageService.getFileStream(storageKey);

            // 2. Extract text
            String extractedText = pdfExtractionService.extractText(fileStream);

            // 3. Generate and Store Embeddings
            if (extractedText != null && !extractedText.isBlank()) {
                if (features.isEmbeddingsEnabled()) {
                    embeddingService.processAndStoreEmbeddings(extractedText, document.getId());
                    document.setEmbeddingStatus(com.lms.model.ProcessingStatus.COMPLETED);
                    generateArtifact(document, ArtifactType.EMBEDDINGS);
                } else {
                    LOG.info("Embeddings generation skipped (feature disabled)");
                    document.setEmbeddingStatus(com.lms.model.ProcessingStatus.COMPLETED);
                }
            } else {
                document.setEmbeddingStatus(com.lms.model.ProcessingStatus.FAILED);
            }
            documentRepository.save(document);

            // 4. Trigger parallel AI generations
            String userId = document.getOwnerId();
            String sourceId = document.getId();
            String topic = document.getOriginalFilename();

            CompletableFuture<Void> summaryTask = CompletableFuture.runAsync(() -> {
                try {
                    updateStatus(documentId, "summaryStatus", com.lms.model.ProcessingStatus.PROCESSING);
                    summaryService.generateSummary(userId, sourceId);
                    updateStatus(documentId, "summaryStatus", com.lms.model.ProcessingStatus.COMPLETED);
                } catch (Exception e) {
                    LOG.error("Summary generation failed for doc {}", sourceId, e);
                    updateStatus(documentId, "summaryStatus", com.lms.model.ProcessingStatus.FAILED);
                }
            }, aiTaskExecutor);

            CompletableFuture<Void> flashcardTask = CompletableFuture.runAsync(() -> {
                try {
                    updateStatus(documentId, "flashcardStatus", com.lms.model.ProcessingStatus.PROCESSING);
                    flashcardService.generateDeck(userId, topic, sourceId);
                    updateStatus(documentId, "flashcardStatus", com.lms.model.ProcessingStatus.COMPLETED);
                } catch (Exception e) {
                    LOG.error("Flashcard generation failed for doc {}", sourceId, e);
                    updateStatus(documentId, "flashcardStatus", com.lms.model.ProcessingStatus.FAILED);
                }
            }, aiTaskExecutor);

            CompletableFuture<Void> quizTask = CompletableFuture.runAsync(() -> {
                try {
                    updateStatus(documentId, "quizStatus", com.lms.model.ProcessingStatus.PROCESSING);
                    questionGenerationService.generateQuizForStudent(userId, topic, sourceId, 5);
                    updateStatus(documentId, "quizStatus", com.lms.model.ProcessingStatus.COMPLETED);
                } catch (Exception e) {
                    LOG.error("Quiz generation failed for doc {}", sourceId, e);
                    updateStatus(documentId, "quizStatus", com.lms.model.ProcessingStatus.FAILED);
                }
            }, aiTaskExecutor);

            CompletableFuture<Void> mindmapTask = CompletableFuture.runAsync(() -> {
                try {
                    updateStatus(documentId, "mindmapStatus", com.lms.model.ProcessingStatus.PROCESSING);
                    mindmapService.generateHierarchicalMindmap(userId, topic, sourceId);
                    updateStatus(documentId, "mindmapStatus", com.lms.model.ProcessingStatus.COMPLETED);
                } catch (Exception e) {
                    LOG.error("Mindmap generation failed for doc {}", sourceId, e);
                    updateStatus(documentId, "mindmapStatus", com.lms.model.ProcessingStatus.FAILED);
                }
            }, aiTaskExecutor);

            CompletableFuture.allOf(summaryTask, flashcardTask, quizTask, mindmapTask).join();

            document = documentRepository.findById(documentId).orElse(document);
            document.setStatus(DocumentStatus.READY);
            documentRepository.save(document);
            LOG.info("Successfully processed document {}", documentId);
        } catch (Exception e) {
            LOG.error("Failed to process document {}", documentId, e);
            document = documentRepository.findById(documentId).orElse(document);
            document.setStatus(DocumentStatus.FAILED);
            documentRepository.save(document);
        }
    }

    private void updateStatus(String docId, String field, com.lms.model.ProcessingStatus status) {
        Document doc = documentRepository.findById(docId).orElse(null);
        if (doc != null) {
            switch (field) {
                case "summaryStatus": doc.setSummaryStatus(status); break;
                case "flashcardStatus": doc.setFlashcardStatus(status); break;
                case "quizStatus": doc.setQuizStatus(status); break;
                case "mindmapStatus": doc.setMindmapStatus(status); break;
            }
            documentRepository.save(doc);
        }
    }

    private void generateArtifact(Document document, ArtifactType type) {
        LOG.info("Generating {} for document {}", type, document.getId());
        DocumentArtifact artifact = new DocumentArtifact();
        artifact.setDocumentId(document.getId());
        artifact.setType(type);
        artifact.setStatus("GENERATED");
        // Simulated S3 key or reference
        artifact.setStorageKey("artifacts/" + document.getId() + "/" + type.name().toLowerCase() + ".json");
        documentArtifactRepository.save(artifact);
    }

    @Transactional
    public void reprocessDocument(String documentId) {
        Document document = documentRepository.findById(documentId).orElseThrow(() -> new IllegalArgumentException("Document not found"));
        document.setStatus(DocumentStatus.UPLOADING);
        document.setSummaryStatus(com.lms.model.ProcessingStatus.PENDING);
        document.setQuizStatus(com.lms.model.ProcessingStatus.PENDING);
        document.setFlashcardStatus(com.lms.model.ProcessingStatus.PENDING);
        document.setMindmapStatus(com.lms.model.ProcessingStatus.PENDING);
        document.setDiscussionStatus(com.lms.model.ProcessingStatus.PENDING);
        document.setEmbeddingStatus(com.lms.model.ProcessingStatus.PENDING);
        documentRepository.save(document);
        
        // Retrigger async pipeline
        processDocument(documentId);
    }

    @Transactional
    public void deleteDocument(String documentId) {
        Document document = documentRepository.findById(documentId).orElse(null);
        if (document == null) {
            return;
        }

        // 1. Delete MinIO objects
        try {
            String storageKey = "documents/" + document.getId() + "/" + document.getOriginalFilename();
            minioStorageService.deleteFile(storageKey);
        } catch (Exception e) {
            LOG.warn("Failed to delete file from MinIO for document {}", documentId, e);
        }

        // 2. Database records are deleted via cascading or manual repository deletions.
        String sourceId = documentId;
        
        try {
            notificationRepository.deleteByResourceId(documentId);
        } catch (Exception e) {
            LOG.warn("Failed to delete notifications for documentId={}: {}", documentId, e.getMessage());
        }

        try {
            embeddingRepository.deleteBySourceId(documentId);
            summaryRepository.findBySourceId(sourceId).ifPresent(summaryRepository::delete);
            mindmapRepository.findByUserIdAndSourceId(document.getOwnerId(), sourceId).ifPresent(mindmapRepository::delete);
            // Flashcards
            deckRepository.findByUserId(document.getOwnerId()).stream()
                .filter(d -> sourceId.equals(d.getSourceId()))
                .forEach(d -> {
                    flashcardRepository.findByDeckId(d.getId()).forEach(flashcardRepository::delete);
                    deckRepository.delete(d);
                });
            // Quizzes: Questions might not have sourceId directly but they are linked, 
            // if Question entity doesn't have sourceId directly, we skip or add a custom query.
            // But let's assume we delete artifacts through document Artifact.
            documentArtifactRepository.deleteAll(documentArtifactRepository.findByDocumentId(documentId));
            
            // Delete Chunks
            mongoTemplate.remove(new Query(Criteria.where("sourceId").is(sourceId)), com.lms.model.DocumentChunk.class);
            // Delete Podcasts
            mongoTemplate.remove(new Query(Criteria.where("sourceId").is(sourceId)), com.lms.model.Podcast.class);
            // Delete Chat Sessions (where sourceIds contains this sourceId)
            java.util.List<com.lms.model.ChatSession> sessions = mongoTemplate.find(new Query(Criteria.where("sourceIds").in(sourceId)), com.lms.model.ChatSession.class);
            for (com.lms.model.ChatSession session : sessions) {
                mongoTemplate.remove(new Query(Criteria.where("sessionId").is(session.getId())), com.lms.model.ChatMessage.class);
                mongoTemplate.remove(session);
            }
        } catch (Exception e) {
            LOG.warn("Failed to cascade delete artifacts for document {}", documentId, e);
        }
        
        documentRepository.delete(document);
        LOG.info("Successfully deleted document and cascaded artifacts for {}", documentId);
    }
}
