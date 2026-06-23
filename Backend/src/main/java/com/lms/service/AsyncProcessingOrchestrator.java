package com.lms.service;

import com.lms.model.DocumentIngestedEvent;
import com.lms.model.Source;
import com.lms.model.Podcast;
import com.lms.model.PodcastJobStatus;
import com.lms.model.SlideExport;
import com.lms.repository.SourceRepository;
import com.lms.repository.PodcastRepository;
import com.lms.repository.SlideExportRepository;
import com.lms.events.*;
import com.lms.config.FeaturesProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Component
public class AsyncProcessingOrchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncProcessingOrchestrator.class);

    private final SourceRepository sourceRepository;
    private final SummaryService summaryService;
    private final FlashcardService flashcardService;
    private final QuestionGenerationService questionGenerationService;
    private final PodcastService podcastService;
    private final PodcastAudioService podcastAudioService;
    private final MindmapService mindmapService;
    private final SlidesGenerationService slidesGenerationService;
    private final Executor aiTaskExecutor;
    private final ApplicationEventPublisher eventPublisher;
    private final MongoTemplate mongoTemplate;
    private final JobStatusService jobStatusService;
    private final KnowledgeGraphService knowledgeGraphService;
    private final PodcastRepository podcastRepository;
    private final SlideExportRepository slideExportRepository;
    private final FeaturesProperties features;

    private final java.util.concurrent.ConcurrentHashMap<String, Boolean> activeJobs = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.Semaphore aiConcurrencySemaphore = new java.util.concurrent.Semaphore(2);

    @Value("${app.ai.timeout-ms:300000}")
    private long aiTimeoutMs;

    public AsyncProcessingOrchestrator(SourceRepository sourceRepository,
                                       SummaryService summaryService,
                                       FlashcardService flashcardService,
                                       QuestionGenerationService questionGenerationService,
                                       PodcastService podcastService,
                                       PodcastAudioService podcastAudioService,
                                       MindmapService mindmapService,
                                       SlidesGenerationService slidesGenerationService,
                                       @Qualifier("aiTaskExecutor") Executor aiTaskExecutor,
                                       ApplicationEventPublisher eventPublisher,
                                       MongoTemplate mongoTemplate,
                                       JobStatusService jobStatusService,
                                       KnowledgeGraphService knowledgeGraphService,
                                       PodcastRepository podcastRepository,
                                       SlideExportRepository slideExportRepository,
                                       FeaturesProperties features) {
        this.sourceRepository = sourceRepository;
        this.summaryService = summaryService;
        this.flashcardService = flashcardService;
        this.questionGenerationService = questionGenerationService;
        this.podcastService = podcastService;
        this.podcastAudioService = podcastAudioService;
        this.mindmapService = mindmapService;
        this.slidesGenerationService = slidesGenerationService;
        this.aiTaskExecutor = aiTaskExecutor;
        this.eventPublisher = eventPublisher;
        this.mongoTemplate = mongoTemplate;
        this.jobStatusService = jobStatusService;
        this.knowledgeGraphService = knowledgeGraphService;
        this.podcastRepository = podcastRepository;
        this.slideExportRepository = slideExportRepository;
        this.features = features;
    }

    private boolean acquireLock(String sourceId, String jobType) {
        String key = sourceId + "_" + jobType;
        return activeJobs.putIfAbsent(key, Boolean.TRUE) == null;
    }

    private void releaseLock(String sourceId, String jobType) {
        String key = sourceId + "_" + jobType;
        activeJobs.remove(key);
    }

    private static final java.util.concurrent.ExecutorService TIMEOUT_EXECUTOR = java.util.concurrent.Executors.newCachedThreadPool();

    private <T> T runWithTimeout(java.util.concurrent.Callable<T> callable, long timeout, TimeUnit unit) throws Exception {
        CompletableFuture<T> future = new CompletableFuture<>();
        TIMEOUT_EXECUTOR.execute(() -> {
            try {
                future.complete(callable.call());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        try {
            return future.get(timeout, unit);
        } catch (java.util.concurrent.ExecutionException e) {
            if (e.getCause() instanceof Exception) {
                throw (Exception) e.getCause();
            } else {
                throw new RuntimeException(e.getCause());
            }
        }
    }

    private void updateSourceStatus(String sourceId, String key, Object status) {
        Query query = new Query(Criteria.where("id").is(sourceId));
        Update update = new Update().set("metadata." + key, status);
        mongoTemplate.updateFirst(query, update, Source.class);
    }

    @EventListener
    @Async
    public void handleDocumentIngested(DocumentIngestedEvent event) {
        String sourceId = event.getSourceId();
        String userId = event.getUserId();

        LOG.info("Async Orchestrator started for sourceId: {}", sourceId);
        long overallStartTime = System.currentTimeMillis();

        Source source = sourceRepository.findById(sourceId).orElse(null);
        if (source == null) return;

        // Initialize all statuses to PENDING
        if (source.getMetadata() == null) {
            source.setMetadata(new java.util.HashMap<>());
        }
        source.getMetadata().put("summaryStatus", "PENDING");
        source.getMetadata().put("flashcardStatus", "PENDING");
        source.getMetadata().put("quizStatus", "PENDING");
        source.getMetadata().put("podcastStatus", "PENDING");
        source.getMetadata().put("mindmapStatus", "PENDING");
        source.getMetadata().put("slidesStatus", "PENDING");
        source.getMetadata().put("knowledgeGraphStatus", "PENDING");
        source.getMetadata().put("status", "PROCESSING");
        source.getMetadata().put("progress", 50);
        sourceRepository.save(source);

        // Save initial job statuses in collection
        jobStatusService.createOrUpdateJob(sourceId, "SUMMARY", "PENDING", "Queued for generation");
        jobStatusService.createOrUpdateJob(sourceId, "FLASHCARDS", "PENDING", "Queued for generation");
        jobStatusService.createOrUpdateJob(sourceId, "QUIZ", "PENDING", "Queued for generation");
        jobStatusService.createOrUpdateJob(sourceId, "PODCAST", "PENDING", "Queued for generation");
        jobStatusService.createOrUpdateJob(sourceId, "MINDMAP", "PENDING", "Queued for generation");
        jobStatusService.createOrUpdateJob(sourceId, "SLIDES", "PENDING", "Queued for generation");
        jobStatusService.createOrUpdateJob(sourceId, "KNOWLEDGEGRAPH", "PENDING", "Queued for generation");

        String topic = (String) source.getMetadata().getOrDefault("extractedTitle", source.getName());
        long timeoutSec = Math.max(30L, aiTimeoutMs / 1000L);

        // STAGE 1: Summary first
        CompletableFuture<Void> summaryFuture = CompletableFuture.runAsync(() -> {
            if (!acquireLock(sourceId, "SUMMARY")) {
                LOG.info("Summary generation already in progress for sourceId: {}", sourceId);
                return;
            }
            try {
                aiConcurrencySemaphore.acquire();
                LOG.info("SUMMARY_STARTED");
                updateSourceStatus(sourceId, "summaryStatus", "PROCESSING");
                jobStatusService.createOrUpdateJob(sourceId, "SUMMARY", "PROCESSING", "Generating summary...");
                eventPublisher.publishEvent(new SummaryGeneratedEvent(this, sourceId, userId, "PROCESSING"));
                try {
                    runWithTimeout(() -> {
                        summaryService.generateSummary(userId, sourceId);
                        return null;
                    }, timeoutSec, TimeUnit.SECONDS);

                    updateSourceStatus(sourceId, "summaryStatus", "COMPLETED");
                    jobStatusService.createOrUpdateJob(sourceId, "SUMMARY", "COMPLETED", "Summary generation completed");
                    eventPublisher.publishEvent(new SummaryGeneratedEvent(this, sourceId, userId, "COMPLETED"));
                    LOG.info("SUMMARY_COMPLETED");
                } catch (Throwable e) {
                    LOG.error("Summary gen failed or timed out", e);
                    updateSourceStatus(sourceId, "summaryStatus", "FAILED");
                    String errMsg = e.getMessage() != null ? e.getMessage() : e.toString();
                    jobStatusService.createOrUpdateJob(sourceId, "SUMMARY", "FAILED", errMsg);
                    eventPublisher.publishEvent(new SummaryGeneratedEvent(this, sourceId, userId, "FAILED"));
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                LOG.error("Summary interrupted waiting for semaphore", ie);
            } finally {
                aiConcurrencySemaphore.release();
                releaseLock(sourceId, "SUMMARY");
            }
        }, aiTaskExecutor);

        // Proceed to Stage 2 even if Summary failed (clean up exceptional completion)
        CompletableFuture<Void> stage1Clean = summaryFuture.exceptionally(ex -> {
            LOG.error("Summary stage completed with exception, proceeding to Stage 2", ex);
            return null;
        });

        // STAGE 2: Flashcards, Quiz, Mindmap, KnowledgeGraph, Slides in background queue (max 2 concurrent AI tasks)
        CompletableFuture<Void> stage2Future = stage1Clean.thenCompose(v -> {
            CompletableFuture<Void> flashcardFuture = CompletableFuture.runAsync(() -> {
                if (!acquireLock(sourceId, "FLASHCARDS")) {
                    LOG.info("Flashcard generation already in progress for sourceId: {}", sourceId);
                    return;
                }
                try {
                    aiConcurrencySemaphore.acquire();
                    LOG.info("FLASHCARDS_STARTED");
                    updateSourceStatus(sourceId, "flashcardStatus", "PROCESSING");
                    jobStatusService.createOrUpdateJob(sourceId, "FLASHCARDS", "PROCESSING", "Generating flashcards...");
                    eventPublisher.publishEvent(new FlashcardsGeneratedEvent(this, sourceId, userId, "PROCESSING"));
                    try {
                        com.lms.model.AIResult<com.lms.model.FlashcardDeck> result = runWithTimeout(() -> {
                            return flashcardService.generateDeck(userId, topic, sourceId);
                        }, timeoutSec, TimeUnit.SECONDS);

                        if (result != null && result.isSuccess()) {
                            updateSourceStatus(sourceId, "flashcardStatus", "COMPLETED");
                            jobStatusService.createOrUpdateJob(sourceId, "FLASHCARDS", "COMPLETED", "Flashcards generation completed");
                            eventPublisher.publishEvent(new FlashcardsGeneratedEvent(this, sourceId, userId, "COMPLETED"));
                            LOG.info("FLASHCARDS_COMPLETED");
                        } else {
                            String err = (result != null && result.getError() != null) ? result.getError() : "Unknown failure";
                            LOG.error("Flashcard generation failed: {}", err);
                            updateSourceStatus(sourceId, "flashcardStatus", "FAILED");
                            jobStatusService.createOrUpdateJob(sourceId, "FLASHCARDS", "FAILED", err);
                            eventPublisher.publishEvent(new FlashcardsGeneratedEvent(this, sourceId, userId, "FAILED"));
                        }
                    } catch (Throwable e) {
                        LOG.error("Flashcard gen failed or timed out", e);
                        updateSourceStatus(sourceId, "flashcardStatus", "FAILED");
                        String errMsg = e.getMessage() != null ? e.getMessage() : e.toString();
                        jobStatusService.createOrUpdateJob(sourceId, "FLASHCARDS", "FAILED", errMsg);
                        eventPublisher.publishEvent(new FlashcardsGeneratedEvent(this, sourceId, userId, "FAILED"));
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    LOG.error("Flashcards interrupted waiting for semaphore", ie);
                } finally {
                    aiConcurrencySemaphore.release();
                    releaseLock(sourceId, "FLASHCARDS");
                }
            }, aiTaskExecutor);

            CompletableFuture<Void> quizFuture = CompletableFuture.runAsync(() -> {
                if (!acquireLock(sourceId, "QUIZ")) {
                    LOG.info("Quiz generation already in progress for sourceId: {}", sourceId);
                    return;
                }
                try {
                    aiConcurrencySemaphore.acquire();
                    LOG.info("QUIZ_STARTED");
                    updateSourceStatus(sourceId, "quizStatus", "PROCESSING");
                    jobStatusService.createOrUpdateJob(sourceId, "QUIZ", "PROCESSING", "Generating quiz...");
                    eventPublisher.publishEvent(new QuizGeneratedEvent(this, sourceId, userId, "PROCESSING"));
                    try {
                        runWithTimeout(() -> {
                            questionGenerationService.generateQuestion(userId, topic, 1, sourceId, "MCQ");
                            return null;
                        }, timeoutSec, TimeUnit.SECONDS);

                        LOG.info("SETTING QUIZ STATUS TO COMPLETED for {}", sourceId);
                        updateSourceStatus(sourceId, "quizStatus", "COMPLETED");
                        jobStatusService.createOrUpdateJob(sourceId, "QUIZ", "COMPLETED", "Quiz generation completed");
                        eventPublisher.publishEvent(new QuizGeneratedEvent(this, sourceId, userId, "COMPLETED"));
                        LOG.info("QUIZ_COMPLETED");
                    } catch (Throwable e) {
                        LOG.error("Quiz gen failed or timed out", e);
                        LOG.info("SETTING QUIZ STATUS TO FAILED for {}", sourceId);
                        updateSourceStatus(sourceId, "quizStatus", "FAILED");
                        String errMsg = e.getMessage() != null ? e.getMessage() : e.toString();
                        jobStatusService.createOrUpdateJob(sourceId, "QUIZ", "FAILED", errMsg);
                        eventPublisher.publishEvent(new QuizGeneratedEvent(this, sourceId, userId, "FAILED"));
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    LOG.error("Quiz interrupted waiting for semaphore", ie);
                } finally {
                    aiConcurrencySemaphore.release();
                    releaseLock(sourceId, "QUIZ");
                }
            }, aiTaskExecutor);

            CompletableFuture<Void> mindmapFuture = CompletableFuture.runAsync(() -> {
                if (!acquireLock(sourceId, "MINDMAP")) {
                    LOG.info("Mindmap generation already in progress for sourceId: {}", sourceId);
                    return;
                }
                try {
                    aiConcurrencySemaphore.acquire();
                    LOG.info("MINDMAP_STARTED");
                    updateSourceStatus(sourceId, "mindmapStatus", "PROCESSING");
                    jobStatusService.createOrUpdateJob(sourceId, "MINDMAP", "PROCESSING", "Generating mindmap...");
                    try {
                        runWithTimeout(() -> {
                            mindmapService.generateHierarchicalMindmap(userId, topic, sourceId);
                            return null;
                        }, timeoutSec, TimeUnit.SECONDS);

                        updateSourceStatus(sourceId, "mindmapStatus", "COMPLETED");
                        jobStatusService.createOrUpdateJob(sourceId, "MINDMAP", "COMPLETED", "Mindmap generation completed");
                        LOG.info("MINDMAP_COMPLETED");
                    } catch (Throwable e) {
                        LOG.error("Mindmap gen failed or timed out", e);
                        updateSourceStatus(sourceId, "mindmapStatus", "FAILED");
                        String errMsg = e.getMessage() != null ? e.getMessage() : e.toString();
                        jobStatusService.createOrUpdateJob(sourceId, "MINDMAP", "FAILED", errMsg);
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    LOG.error("Mindmap interrupted waiting for semaphore", ie);
                } finally {
                    aiConcurrencySemaphore.release();
                    releaseLock(sourceId, "MINDMAP");
                }
            }, aiTaskExecutor);

            CompletableFuture<Void> knowledgeGraphFuture = CompletableFuture.runAsync(() -> {
                if (!features.isKnowledgeGraphEnabled()) {
                    LOG.info("Knowledge Graph generation skipped (feature disabled)");
                    updateSourceStatus(sourceId, "knowledgeGraphStatus", "COMPLETED");
                    jobStatusService.createOrUpdateJob(sourceId, "KNOWLEDGEGRAPH", "COMPLETED", "Knowledge Graph generation skipped (feature disabled)");
                    return;
                }
                if (!acquireLock(sourceId, "KNOWLEDGEGRAPH")) {
                    LOG.info("Knowledge Graph generation already in progress for sourceId: {}", sourceId);
                    return;
                }
                try {
                    aiConcurrencySemaphore.acquire();
                    LOG.info("KNOWLEDGEGRAPH_STARTED");
                    updateSourceStatus(sourceId, "knowledgeGraphStatus", "PROCESSING");
                    jobStatusService.createOrUpdateJob(sourceId, "KNOWLEDGEGRAPH", "PROCESSING", "Generating knowledge graph...");
                    try {
                        runWithTimeout(() -> {
                            knowledgeGraphService.generateKnowledgeGraph(userId, topic, sourceId);
                            return null;
                        }, timeoutSec, TimeUnit.SECONDS);

                        updateSourceStatus(sourceId, "knowledgeGraphStatus", "COMPLETED");
                        jobStatusService.createOrUpdateJob(sourceId, "KNOWLEDGEGRAPH", "COMPLETED", "Knowledge graph generation completed");
                        LOG.info("KNOWLEDGEGRAPH_COMPLETED");
                    } catch (Throwable e) {
                        LOG.error("Knowledge graph gen failed or timed out", e);
                        updateSourceStatus(sourceId, "knowledgeGraphStatus", "FAILED");
                        String errMsg = e.getMessage() != null ? e.getMessage() : e.toString();
                        jobStatusService.createOrUpdateJob(sourceId, "KNOWLEDGEGRAPH", "FAILED", errMsg);
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    LOG.error("KnowledgeGraph interrupted waiting for semaphore", ie);
                } finally {
                    aiConcurrencySemaphore.release();
                    releaseLock(sourceId, "KNOWLEDGEGRAPH");
                }
            }, aiTaskExecutor);

            CompletableFuture<Void> slidesFuture = CompletableFuture.runAsync(() -> {
                if (!features.isSlidesEnabled()) {
                    LOG.info("Slides generation skipped (feature disabled)");
                    updateSourceStatus(sourceId, "slidesStatus", "COMPLETED");
                    jobStatusService.createOrUpdateJob(sourceId, "SLIDES", "COMPLETED", "Slides generation skipped (feature disabled)");
                    return;
                }
                if (!acquireLock(sourceId, "SLIDES")) {
                    LOG.info("Slides generation already in progress for sourceId: {}", sourceId);
                    return;
                }
                LOG.info("SLIDES_STARTED");
                updateSourceStatus(sourceId, "slidesStatus", "PROCESSING");
                jobStatusService.createOrUpdateJob(sourceId, "SLIDES", "PROCESSING", "Generating slides...");
                try {
                    SlideExport export = slideExportRepository.findByUserIdAndSourceId(userId, sourceId)
                            .orElseGet(() -> {
                                SlideExport se = new SlideExport(userId, sourceId);
                                se.setStatus("PROCESSING");
                                return slideExportRepository.save(se);
                            });

                    runWithTimeout(() -> {
                        slidesGenerationService.generateExportAsync(export.getId(), userId, sourceId).get(timeoutSec, TimeUnit.SECONDS);
                        return null;
                    }, timeoutSec, TimeUnit.SECONDS);

                    updateSourceStatus(sourceId, "slidesStatus", "COMPLETED");
                    jobStatusService.createOrUpdateJob(sourceId, "SLIDES", "COMPLETED", "Slides generation completed");
                    LOG.info("SLIDES_COMPLETED");
                } catch (Throwable e) {
                    LOG.error("Slides gen failed or timed out", e);
                    updateSourceStatus(sourceId, "slidesStatus", "FAILED");
                    String errMsg = e.getMessage() != null ? e.getMessage() : e.toString();
                    jobStatusService.createOrUpdateJob(sourceId, "SLIDES", "FAILED", errMsg);
                } finally {
                    releaseLock(sourceId, "SLIDES");
                }
            }, aiTaskExecutor);

            return CompletableFuture.allOf(flashcardFuture, quizFuture, mindmapFuture, knowledgeGraphFuture, slidesFuture);
        });

        // Proceed to Podcast stage even if Stage 2 failed
        CompletableFuture<Void> stage2Clean = stage2Future.exceptionally(ex -> {
            LOG.error("Stage 2 completed with exception, proceeding to Stage 3", ex);
            return null;
        });

        // STAGE 3: Podcast last
        CompletableFuture<Void> stage3Future = stage2Clean.thenCompose(v -> {
            return CompletableFuture.runAsync(() -> {
                if (!acquireLock(sourceId, "PODCAST")) {
                    LOG.info("Podcast generation already in progress for sourceId: {}", sourceId);
                    return;
                }
                try {
                    aiConcurrencySemaphore.acquire();
                    LOG.info("PODCAST_STARTED");
                    updateSourceStatus(sourceId, "podcastStatus", "PROCESSING");
                    jobStatusService.createOrUpdateJob(sourceId, "PODCAST", "PROCESSING", "Generating podcast script...");
                    eventPublisher.publishEvent(new PodcastGeneratedEvent(this, sourceId, userId, "PROCESSING"));
                    try {
                        Podcast podcast = podcastRepository.findByUserIdAndSourceId(userId, sourceId)
                                .orElseGet(() -> {
                                    Podcast p = new Podcast();
                                    p.setUserId(userId);
                                    p.setTopic(topic);
                                    p.setSourceId(sourceId);
                                    p.setStatus(PodcastJobStatus.PROCESSING);
                                    return podcastRepository.save(p);
                                });

                        runWithTimeout(() -> {
                            podcastService.generatePodcastAsync(podcast.getId(), userId, topic, sourceId).get(timeoutSec, TimeUnit.SECONDS);
                            return null;
                        }, timeoutSec, TimeUnit.SECONDS);

                        updateSourceStatus(sourceId, "podcastStatus", "COMPLETED");
                        jobStatusService.createOrUpdateJob(sourceId, "PODCAST", "COMPLETED", "Podcast script generation completed");
                        eventPublisher.publishEvent(new PodcastGeneratedEvent(this, sourceId, userId, "COMPLETED"));
                        LOG.info("PODCAST_COMPLETED");
                    } catch (Throwable e) {
                        LOG.error("Podcast gen failed or timed out", e);
                        updateSourceStatus(sourceId, "podcastStatus", "FAILED");
                        String errMsg = e.getMessage() != null ? e.getMessage() : e.toString();
                        jobStatusService.createOrUpdateJob(sourceId, "PODCAST", "FAILED", errMsg);
                        eventPublisher.publishEvent(new PodcastGeneratedEvent(this, sourceId, userId, "FAILED"));
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    LOG.error("Podcast interrupted waiting for semaphore", ie);
                } finally {
                    aiConcurrencySemaphore.release();
                    releaseLock(sourceId, "PODCAST");
                }
            }, aiTaskExecutor);
        });

        // Pipeline Completion handler
        stage3Future.thenRun(() -> {
            updateSourceStatus(sourceId, "status", "COMPLETED");
            updateSourceStatus(sourceId, "progress", 100);
            LOG.info("PIPELINE_COMPLETE");
            LOG.info("SOURCE STATUS -> COMPLETED");
            long overallLatency = System.currentTimeMillis() - overallStartTime;
            LOG.info("TOTAL PROCESSING LATENCY: {} ms", overallLatency);
        }).exceptionally(ex -> {
            LOG.error("Pipeline failed critical step", ex);
            updateSourceStatus(sourceId, "status", "FAILED");
            updateSourceStatus(sourceId, "progress", 0);
            return null;
        });
    }
}
