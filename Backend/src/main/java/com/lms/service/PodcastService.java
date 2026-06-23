package com.lms.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.model.DocumentChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import com.lms.model.Podcast;
import com.lms.model.PodcastJobStatus;
import com.lms.model.Source;
import com.lms.repository.PodcastRepository;
import com.lms.repository.SourceRepository;
import com.lms.repository.SummaryRepository;
import com.lms.model.Summary;
import io.github.resilience4j.retry.annotation.Retry;

import org.springframework.beans.factory.ObjectProvider;

@Service
public class PodcastService {

    private static final Logger LOG = LoggerFactory.getLogger(PodcastService.class);

    private final HybridRetrievalService retrievalService;
    private final ContextBuilder contextBuilder;
    private final com.lms.service.ai.AIOrchestratorService aiOrchestratorService;
    private final ObjectMapper objectMapper;
    private final ObjectMapper relaxedMapper;
    private final PodcastRepository podcastRepository;
    private final AIResponseValidator aiValidator;
    private final PodcastAudioService podcastAudioService;
    private final ObjectProvider<PodcastService> podcastServiceProvider;
    private final JobStatusService jobStatusService;
    private final SummaryRepository summaryRepository;
    private final SourceRepository sourceRepository;

    public PodcastService(HybridRetrievalService retrievalService, ContextBuilder contextBuilder,
                          com.lms.service.ai.AIOrchestratorService aiOrchestratorService, ObjectMapper objectMapper, PodcastRepository podcastRepository,
                          AIResponseValidator aiValidator, PodcastAudioService podcastAudioService,
                          ObjectProvider<PodcastService> podcastServiceProvider, JobStatusService jobStatusService,
                          SummaryRepository summaryRepository, SourceRepository sourceRepository) {
        this.retrievalService = retrievalService;
        this.contextBuilder = contextBuilder;
        this.aiOrchestratorService = aiOrchestratorService;
        this.objectMapper = objectMapper;
        this.podcastRepository = podcastRepository;
        this.aiValidator = aiValidator;
        this.podcastAudioService = podcastAudioService;
        this.podcastServiceProvider = podcastServiceProvider;
        this.jobStatusService = jobStatusService;
        this.summaryRepository = summaryRepository;
        this.sourceRepository = sourceRepository;
        
        this.relaxedMapper = new ObjectMapper();
        this.relaxedMapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
        this.relaxedMapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
        this.relaxedMapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, true);
    }

    public String submitPodcastGeneration(String userId, String topic, String sourceId) {
        java.util.Optional<Podcast> existing = podcastRepository.findByUserIdAndSourceId(userId, sourceId);
        if (existing.isPresent()) {
            return existing.get().getId();
        }

        Podcast podcast = new Podcast();
        podcast.setUserId(userId);
        podcast.setTopic(topic);
        podcast.setSourceId(sourceId);
        podcast.setStatus(PodcastJobStatus.PROCESSING);
        podcast = podcastRepository.save(podcast);

        jobStatusService.createOrUpdateJob(sourceId, "PODCAST", "PROCESSING", "Generating podcast script...");

        podcastServiceProvider.getIfAvailable().generatePodcastAsync(podcast.getId(), userId, topic, sourceId);
        return podcast.getId();
    }

    @Async
    @Retry(name = "aiService", fallbackMethod = "generatePodcastFallback")
    public CompletableFuture<List<Map<String, String>>> generatePodcastAsync(String podcastId, String userId, String topic, String sourceId) {
        String contextText = "";
        int chunkCount = 0;
        if (sourceId != null) {
            java.util.Optional<Summary> summaryOpt = summaryRepository.findBySourceId(sourceId);
            if (summaryOpt.isPresent() && summaryOpt.get().getSummary() != null && !summaryOpt.get().getSummary().isBlank()) {
                contextText = summaryOpt.get().getSummary();
                LOG.info("PODCAST: Using generated Summary as context for sourceId: {}", sourceId);
            }
        }

        if (contextText.isEmpty()) {
            Source source = null;
            if (sourceId != null) {
                source = sourceRepository.findById(sourceId).orElse(null);
            }
            if (source != null && source.getExtractedText() != null && !source.getExtractedText().isBlank()) {
                String context = source.getExtractedText();
                if (context.length() > 1500) {
                    context = context.substring(0, 1500);
                }
                LOG.info("TRUNCATED_CONTEXT_LENGTH={}", context.length());
                contextText = context;
                LOG.info("PODCAST: Using source.getExtractedText() for sourceId: {}", sourceId);
            } else {
                List<DocumentChunk> chunks = retrievalService.retrieve(userId, topic, sourceId != null ? List.of(sourceId) : List.of(), 6);
                ContextBuilder.RagContext ragContext = contextBuilder.buildContext(chunks);
                contextText = ragContext.getContextText();
                LOG.info("PODCAST: Using retrieved chunks for sourceId: {}", sourceId);
            }
        }

        LOG.info("SOURCE_ID={}", sourceId);
        LOG.info("CONTEXT_LENGTH={}", contextText.length());
        LOG.info("CONTEXT_PREVIEW={}", contextText.substring(0, Math.min(1000, contextText.length())));

        String prompt = "Create ONLY 2 short podcast segments about " + topic + " based on this text:\n" +
                contextText + "\n\n" +
                "Each segment must contain under 50 words. The hosts are 'Host 1' and 'Host 2'.\n" +
                "Respond ONLY with valid JSON. Do not include any explanation or markdown. Format: { \"title\": \"string\", \"segments\": [{ \"speaker\": \"string\", \"text\": \"string\" }] }";

        long startTime = System.currentTimeMillis();
        try {
            String aiResponseText = aiOrchestratorService.generate(prompt, Map.of("format", "json"));
            long latency = System.currentTimeMillis() - startTime;
            
            LOG.info("RAW AI RESPONSE (Podcast):\n{}", aiResponseText);
            try {
                java.nio.file.Files.writeString(java.nio.file.Path.of("debug-podcast.json"), aiResponseText);
            } catch (Exception ex) {
                LOG.warn("Failed to write debug-podcast.json: {}", ex.getMessage());
            }
            
            aiValidator.logAIRequest(userId, "/podcast/generate", "mistral", latency, aiResponseText, true);

            // Strip markdown fences
            String preProcessed = aiResponseText.replaceAll("```json", "").replaceAll("```", "").trim();
            String cleanJson = aiValidator.extractJson(preProcessed);
            cleanJson = aiValidator.repairJson(cleanJson);

            String cleaned = cleanJson.trim();
            if (!cleaned.endsWith("}") && !cleaned.endsWith("]")) {
                throw new com.lms.exception.AIServiceException("Incomplete JSON response from AI");
            }

            boolean isValidJson = (cleaned.startsWith("{") && cleaned.endsWith("}")) || (cleaned.startsWith("[") && cleaned.endsWith("]"));
            if (!isValidJson) {
                throw new RuntimeException("Not a valid JSON object or array: " + (cleanJson.length() > 60 ? cleanJson.substring(0, 60) + "..." : cleanJson));
            }

            try {
                List<Map<String, String>> result;
                try {
                    result = relaxedMapper.readValue(cleanJson, new TypeReference<List<Map<String, String>>>() {});
                } catch (Exception parseEx) {
                    com.fasterxml.jackson.databind.JsonNode rootNode = relaxedMapper.readTree(cleanJson);
                    if (rootNode.isArray()) {
                        result = relaxedMapper.convertValue(rootNode, new TypeReference<List<Map<String, String>>>() {});
                    } else {
                        java.util.Iterator<Map.Entry<String, com.fasterxml.jackson.databind.JsonNode>> fields = rootNode.fields();
                        com.fasterxml.jackson.databind.JsonNode arrayNode = null;
                        while (fields.hasNext()) {
                            Map.Entry<String, com.fasterxml.jackson.databind.JsonNode> field = fields.next();
                            if (field.getValue().isArray()) {
                                arrayNode = field.getValue();
                                break;
                            }
                        }
                        if (arrayNode != null) {
                            result = relaxedMapper.convertValue(arrayNode, new TypeReference<List<Map<String, String>>>() {});
                        } else {
                            throw parseEx;
                        }
                    }
                }
                
                if (result != null) {
                    for (Map<String, String> segment : result) {
                        if (segment.containsKey("text") && !segment.containsKey("line")) {
                            segment.put("line", segment.get("text"));
                        }
                    }
                }

                final List<Map<String, String>> finalResult = result;
                podcastRepository.findById(podcastId).ifPresent(podcast -> {
                    podcast.setScript(finalResult);
                    podcast.setStatus(PodcastJobStatus.COMPLETED);
                    podcast.setAudioStatus(PodcastJobStatus.PENDING);
                    podcast.setGenerationSource("AI");
                    podcastRepository.save(podcast);
                    LOG.info("PODCAST_COMPLETED: sourceId={}", sourceId);
                    LOG.info("PODCAST SOURCE = AI");
                    
                    // Trigger async audio generation
                    podcastAudioService.generateAudio(podcastId);
                });
                
                jobStatusService.createOrUpdateJob(sourceId, "PODCAST", "COMPLETED", "Podcast script generated");
                
                return CompletableFuture.completedFuture(result);
            } catch (Exception e) {
                LOG.error("Failed to parse podcast JSON: {}", cleanJson, e);
                podcastRepository.findById(podcastId).ifPresent(podcast -> {
                    podcast.setStatus(PodcastJobStatus.FAILED);
                    podcast.setAudioStatus(PodcastJobStatus.FAILED);
                    podcastRepository.save(podcast);
                });
                throw new com.lms.exception.AIServiceException("Podcast script parsing failed", e);
            }
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            LOG.error("Failed to generate podcast: {}", e.getMessage(), e);
            aiValidator.logAIRequest(userId, "/podcast/generate", "mistral", latency, e.getMessage(), false);
            podcastRepository.findById(podcastId).ifPresent(podcast -> {
                podcast.setStatus(PodcastJobStatus.FAILED);
                podcast.setAudioStatus(PodcastJobStatus.FAILED);
                podcastRepository.save(podcast);
            });
            throw new com.lms.exception.AIServiceException("Podcast script generation failed", e);
        }
    }

    public CompletableFuture<List<Map<String, String>>> generatePodcastFallback(String podcastId, String userId, String topic, String sourceId, Throwable t) {
        LOG.error("PODCAST FALLBACK TRIGGERED. Root cause class={}, message={}",
                t.getClass().getSimpleName(), t.getMessage(), t);
        podcastRepository.findById(podcastId).ifPresent(podcast -> {
            podcast.setStatus(PodcastJobStatus.FAILED);
            podcast.setAudioStatus(PodcastJobStatus.FAILED);
            podcastRepository.save(podcast);
        });
        CompletableFuture<List<Map<String, String>>> future = new CompletableFuture<>();
        future.completeExceptionally(new com.lms.exception.AIServiceException("Podcast script generation failed: " + t.getMessage(), t));
        return future;
    }
}

