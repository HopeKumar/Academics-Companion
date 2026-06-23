package com.lms.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.exception.SlideGenerationException;
import com.lms.model.AIResult;
import com.lms.model.DocumentChunk;
import com.lms.model.SlideDeckResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SlideService {

    private static final Logger LOG = LoggerFactory.getLogger(SlideService.class);

    private final HybridRetrievalService retrievalService;
    private final ContextBuilder contextBuilder;
    private final com.lms.service.ai.AIOrchestratorService aiOrchestratorService;
    private final ObjectMapper objectMapper;
    private final AIResponseValidator aiValidator;

    public SlideService(HybridRetrievalService retrievalService, ContextBuilder contextBuilder,
                        com.lms.service.ai.AIOrchestratorService aiOrchestratorService, ObjectMapper objectMapper, AIResponseValidator aiValidator) {
        this.retrievalService = retrievalService;
        this.contextBuilder = contextBuilder;
        this.aiOrchestratorService = aiOrchestratorService;
        this.objectMapper = objectMapper;
        this.aiValidator = aiValidator;
    }

    // @CircuitBreaker(name = "aiService", fallbackMethod = "generateSlidesFallback")
    @Retry(name = "aiService", fallbackMethod = "generateSlidesFallback")
    public AIResult<List<SlideDeckResponse>> generateSlides(String userId, String topic, String sourceId) {
        List<DocumentChunk> chunks = retrievalService.retrieve(userId, topic, sourceId != null ? List.of(sourceId) : List.of(), 6);
        ContextBuilder.RagContext ragContext = contextBuilder.buildContext(chunks);

        String prompt = "Generate a 5-slide presentation outline about " + topic + " based on this text:\n" +
                ragContext.getContextText() + "\n\n" +
                "Output ONLY a JSON array of objects. Each object represents a slide and must have 'title' (string), 'bulletPoints' (array of strings), and 'speakerNotes' (string). Do not include markdown code block styling.";

        long startTime = System.currentTimeMillis();
        try {
            String aiResponseText = aiOrchestratorService.generate(prompt, Map.of());
            long latency = System.currentTimeMillis() - startTime;
            
            LOG.info("SLIDES RAW AI RESPONSE (first 300 chars): {}",
                    aiResponseText.substring(0, Math.min(300, aiResponseText.length())));
            aiValidator.logAIRequest(userId, "/slides/generate", "mistral", latency, aiResponseText, true);

            String cleanJson = aiValidator.extractJson(aiResponseText);
            cleanJson = aiValidator.repairJson(cleanJson);
            LOG.info("SLIDES EXTRACTED JSON (first 300 chars): {}",
                    cleanJson.substring(0, Math.min(300, cleanJson.length())));

            List<SlideDeckResponse> slides;
            try {
                slides = objectMapper.readValue(cleanJson, new TypeReference<List<SlideDeckResponse>>() {});
            } catch (Exception e) {
                LOG.error("SLIDES JSON PARSE FAILURE: extracted_json={}\nError: {}", cleanJson, e.getMessage(), e);
                return AIResult.failure("Slide JSON parse failed: " + e.getMessage(), "Mistral", "mistral", latency);
            }

            return AIResult.success(slides, "Mistral", "mistral", latency);

        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            LOG.error("Failed to generate slides: {}", e.getMessage(), e);
            aiValidator.logAIRequest(userId, "/slides/generate", "mistral", latency, e.getMessage(), false);
            throw new SlideGenerationException("Slide generation failed: " + e.getMessage());
        }
    }

    public AIResult<List<SlideDeckResponse>> generateSlidesFallback(String userId, String topic, String sourceId, Throwable t) {
        LOG.error("SLIDES FALLBACK TRIGGERED. Root cause class={}, message={}",
                t.getClass().getSimpleName(), t.getMessage(), t);
        String realError = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
        return AIResult.failure("Slide generation failed: " + realError, "Mistral", "mistral", 0);
    }
}
