package com.lms.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.lms.dto.ai.SummaryResponse;
import com.lms.model.DocumentChunk;
import com.lms.model.Source;
import com.lms.model.Summary;
import com.lms.repository.DocumentChunkRepository;
import com.lms.repository.SourceRepository;
import com.lms.repository.SummaryRepository;
import com.lms.service.ai.AIOrchestratorService;

import io.github.resilience4j.retry.annotation.Retry;

@Service
public class SummaryService {

    private static final Logger LOG = LoggerFactory.getLogger(SummaryService.class);

    private final SourceRepository sourceRepository;
    private final SummaryRepository summaryRepository;
    private final HybridRetrievalService retrievalService;
    private final ContextBuilder contextBuilder;
    private final AIOrchestratorService aiOrchestratorService;
    private final JobStatusService jobStatusService;
    private final org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;
    private final DocumentChunkRepository chunkRepository;

    @Autowired
    public SummaryService(SourceRepository sourceRepository,
                          SummaryRepository summaryRepository,
                          HybridRetrievalService retrievalService,
                          ContextBuilder contextBuilder,
                          AIOrchestratorService aiOrchestratorService,
                          JobStatusService jobStatusService,
                          org.springframework.data.mongodb.core.MongoTemplate mongoTemplate,
                          DocumentChunkRepository chunkRepository) {
        this.sourceRepository = sourceRepository;
        this.summaryRepository = summaryRepository;
        this.retrievalService = retrievalService;
        this.contextBuilder = contextBuilder;
        this.aiOrchestratorService = aiOrchestratorService;
        this.jobStatusService = jobStatusService;
        this.mongoTemplate = mongoTemplate;
        this.chunkRepository = chunkRepository;
    }
    @Async
    public void generateSummaryAsync(String userId, String sourceId) {
        jobStatusService.createOrUpdateJob(sourceId, "SUMMARY", "PROCESSING", "Generating summary...");
        try {
            generateSummary(userId, sourceId);
            jobStatusService.createOrUpdateJob(sourceId, "SUMMARY", "COMPLETED", "Summary generated successfully");
        } catch (Exception e) {
            LOG.error("Async generation failed for sourceId {}: {}", sourceId, e.getMessage());
            jobStatusService.createOrUpdateJob(sourceId, "SUMMARY", "FAILED", e.getMessage());
        }
    }

    @Retry(name = "aiService", fallbackMethod = "generateSummaryFallback")
    public Summary generateSummary(String userId, String sourceId) {
        synchronized (sourceId.intern()) {
            LOG.info("SUMMARY GENERATION START");
            LOG.info("[SUMMARY_START] REQUEST received for sourceId: {}", sourceId);
            long startTime = System.currentTimeMillis();
            
            // Check ownership using SourceRepository
            Source source = sourceRepository.findById(sourceId)
                    .orElseThrow(() -> new IllegalArgumentException("Source not found"));
            if (!source.getUserId().equals(userId)) {
                throw new SecurityException("Unauthorized access to source");
            }

            // Return cached if exists
            Optional<Summary> existingSummary = summaryRepository.findBySourceId(sourceId);
            if (existingSummary.isPresent()) {
                LOG.info("Returning cached summary for sourceId: {}", sourceId);
                return existingSummary.get();
            }

            String documentContent = "";
            if (source.getExtractedText() != null && !source.getExtractedText().isBlank()) {
                String context = source.getExtractedText();
                if (context.length() > 4500) {
                    context = context.substring(0, 3500);
                }
                LOG.info("TRUNCATED_CONTEXT_LENGTH={}", context.length());
                documentContent = context;
                LOG.info("SUMMARY: Using source.getExtractedText() for sourceId: {}", sourceId);
            } else {
                // Retrieve first 5 chunks chronologically
                List<DocumentChunk> chunks = chunkRepository.findBySourceId(sourceId).stream()
                        .sorted(java.util.Comparator.comparingInt(DocumentChunk::getPageNumber))
                        .limit(5)
                        .collect(Collectors.toList());
                if (chunks.isEmpty()) {
                    throw new IllegalStateException("No text content found for this source");
                }
                ContextBuilder.RagContext ragContext = contextBuilder.buildContext(chunks);
                documentContent = ragContext.getContextText();
                LOG.info("SUMMARY: Using retrieved chunks for sourceId: {}", sourceId);
            }

            LOG.info("SOURCE_ID={}", sourceId);
            LOG.info("CONTEXT_LENGTH={}", documentContent.length());
            LOG.info("CONTEXT_PREVIEW={}", documentContent.substring(0, Math.min(1000, documentContent.length())));

            LOG.info("Generating structured summary via AIOrchestratorService for text length {}", documentContent.length());
            
            try {
                SummaryResponse summaryResponse = aiOrchestratorService.generateSummary(documentContent);
                if (summaryResponse == null || summaryResponse.summary() == null || summaryResponse.summary().isBlank()) {
                    throw new com.lms.exception.AIServiceException("AI summary generation returned empty text");
                }
                
                Summary summary = new Summary();
                summary.setSourceId(sourceId);
                summary.setGenerationSource("AI");
                summary.setSummary(summaryResponse.summary());
                
                // Populate structured fields from AI response
                summary.setKeyPoints(
                    summaryResponse.keyPoints() != null && !summaryResponse.keyPoints().isEmpty()
                        ? summaryResponse.keyPoints()
                        : extractFallbackKeyPoints(summaryResponse.summary())
                );
                
                summary.setImportantTakeaways(
                    summaryResponse.importantTakeaways() != null && !summaryResponse.importantTakeaways().isEmpty()
                        ? summaryResponse.importantTakeaways()
                        : List.of("Review the full summary above for key takeaways")
                );
                
                summary.setDefinitions(
                    summaryResponse.definitions() != null
                        ? summaryResponse.definitions().stream()
                            .map(d -> d.term() + ": " + d.definition())
                            .collect(Collectors.toList())
                        : new ArrayList<>()
                );
                
                summary.setNextSteps(
                    summaryResponse.nextSteps() != null && !summaryResponse.nextSteps().isEmpty()
                        ? summaryResponse.nextSteps()
                        : List.of("Generate flashcards to test your understanding", "Take a quiz on this material")
                );

                org.springframework.data.mongodb.core.query.Query query = new org.springframework.data.mongodb.core.query.Query(org.springframework.data.mongodb.core.query.Criteria.where("sourceId").is(sourceId));
                org.springframework.data.mongodb.core.query.Update update = new org.springframework.data.mongodb.core.query.Update()
                        .set("summary", summary.getSummary())
                        .set("keyPoints", summary.getKeyPoints())
                        .set("importantTakeaways", summary.getImportantTakeaways())
                        .set("definitions", summary.getDefinitions())
                        .set("nextSteps", summary.getNextSteps())
                        .set("generatedAt", summary.getGeneratedAt())
                        .set("generationSource", "AI");
                
                mongoTemplate.upsert(query, update, Summary.class);
                LOG.info("SUMMARY_COMPLETED: sourceId={}", sourceId);
                LOG.info("SUMMARY SOURCE = AI");
                LOG.info("SUMMARY GENERATION COMPLETE");
                Summary savedSummary = summaryRepository.findBySourceId(sourceId).orElse(summary);

                long latency = System.currentTimeMillis() - startTime;
                LOG.info("[SUMMARY_COMPLETE] Generated and saved structured summary for sourceId: {} in {}ms (keyPoints={}, takeaways={}, definitions={})",
                        sourceId, latency, savedSummary.getKeyPoints().size(), savedSummary.getImportantTakeaways().size(), savedSummary.getDefinitions().size());
                
                return savedSummary;

            } catch (Exception e) {
                LOG.error("FAILURE: Error generating summary for sourceId: {} - {}", sourceId, e.getMessage(), e);
                if (e instanceof com.lms.exception.AIServiceException) {
                    throw (com.lms.exception.AIServiceException) e;
                }
                throw new com.lms.exception.AIServiceException("Failed to generate summary: " + e.getMessage(), e);
            }
        }
    }
    
    public Summary getSummary(String userId, String sourceId) {
        // Check ownership
        Source source = sourceRepository.findById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Source not found"));
        if (!source.getUserId().equals(userId)) {
            throw new SecurityException("Unauthorized access to source");
        }
        
        Summary summary = summaryRepository.findBySourceId(sourceId)
                .orElseThrow(() -> new com.lms.exception.ResourceNotFoundException("Summary", sourceId));
                
        if (summary.getSummary() == null || summary.getSummary().trim().isEmpty()) {
            summary.setSummary("Summary content is currently unavailable. Please review the key points below.");
            summaryRepository.save(summary);
        }
        
        return summary;
    }

    public Summary generateSummaryFallback(String userId, String sourceId, Throwable t) {
        LOG.error("Summary generation FALLBACK for doc {}. Error: {}", sourceId, t.getMessage());
        throw new RuntimeException("Summary generation failed: " + t.getMessage(), t);
    }

    /**
     * If the AI didn't return structured key points, extract simple sentences from the summary text.
     */
    private List<String> extractFallbackKeyPoints(String summaryText) {
        if (summaryText == null || summaryText.isBlank()) {
            return List.of("No key points could be extracted");
        }
        String[] sentences = summaryText.split("(?<=[.!?])\\s+");
        List<String> points = new ArrayList<>();
        for (int i = 0; i < Math.min(5, sentences.length); i++) {
            String s = sentences[i].trim();
            if (s.length() > 10) {
                points.add(s);
            }
        }
        return points.isEmpty() ? List.of("See the summary above") : points;
    }
}
