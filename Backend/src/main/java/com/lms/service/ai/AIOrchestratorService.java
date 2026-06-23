package com.lms.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.config.AppConfig;
import com.lms.dto.ai.*;
import com.lms.exception.AIServiceException;
import com.lms.service.AiMetricsService;
import com.lms.service.AIResponseValidator;
import com.lms.service.LlmProvider;
import com.lms.util.InMemoryCache;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@Service
public class AIOrchestratorService {

    private static final Logger LOG = LoggerFactory.getLogger(AIOrchestratorService.class);
    private static final String MODEL_NAME = "mistral";

    private final LlmProvider llmProvider;
    private final com.lms.service.ai.provider.PromptManager promptManager;
    private final InMemoryCache cache;
    private final AiMetricsService metricsService;
    private final AIResponseValidator responseValidator;
    private final ObjectMapper objectMapper;
    private final com.lms.metrics.MetricsRegistry metricsRegistry;

    public AIOrchestratorService(com.lms.service.ai.provider.MistralProvider mistralProvider,
                                 com.lms.service.ai.provider.PromptManager promptManager,
                                 InMemoryCache cache,
                                 AiMetricsService metricsService,
                                 AIResponseValidator responseValidator,
                                 ObjectMapper objectMapper,
                                 com.lms.metrics.MetricsRegistry metricsRegistry) {
        this.llmProvider = mistralProvider;
        this.promptManager = promptManager;
        this.cache = cache;
        this.metricsService = metricsService;
        this.responseValidator = responseValidator;
        this.objectMapper = objectMapper;
        this.metricsRegistry = metricsRegistry;
    }

    /**
     * Synchronous text generation with caching, retries, and circuit breaking.
     */
    // @CircuitBreaker(name = "aiService", fallbackMethod = "generateFallback")
    @Retry(name = "aiService")
    public String generate(String prompt, Map<String, Object> options) {
        metricsService.recordRequest();
        long startTime = System.currentTimeMillis();

        String cacheKey = InMemoryCache.buildKey("ai:generate", prompt);
        final String[] cachedResult = {null};
        cache.get(cacheKey, val -> cachedResult[0] = val);
        if (cachedResult[0] != null) {
            LOG.info("AI Orchestrator Cache Hit (generate)");
            metricsService.recordSuccess(System.currentTimeMillis() - startTime);
            return cachedResult[0];
        }

        try {
            LOG.info("AI Orchestrator: Generating via LlmProvider for prompt length {}", prompt.length());
            String response = llmProvider.generate(prompt, options);
            if (response == null || response.isBlank()) {
                throw new AIServiceException("Empty response received from LLM Provider");
            }

            long latency = System.currentTimeMillis() - startTime;
            metricsService.recordSuccess(latency);
            
            // Log details & account estimated tokens (~4 chars per token)
            responseValidator.logAIRequest(null, "generate", MODEL_NAME, latency, response, true);

            cache.setWithTtl(cacheKey, response, AppConfig.REDIS_AI_EVAL_TTL_SECONDS);
            return response;
        } catch (Exception e) {
            metricsService.recordFailure();
            LOG.error("AI Orchestrator generation error: {}", e.getMessage(), e);
            throw new AIServiceException("AI request failed", e);
        }
    }

    /**
     * Streaming text generation without caching (since it is a real-time stream).
     */
    public Flux<String> generateStream(String prompt, Map<String, Object> options) {
        metricsService.recordRequest();
        long startTime = System.currentTimeMillis();
        try {
            return llmProvider.generateStream(prompt, options)
                    .doOnComplete(() -> metricsService.recordSuccess(System.currentTimeMillis() - startTime))
                    .doOnError(e -> {
                        metricsService.recordFailure();
                        LOG.error("AI Orchestrator stream error: {}", e.getMessage());
                    });
        } catch (Exception e) {
            metricsService.recordFailure();
            return Flux.error(new AIServiceException("AI stream request failed", e));
        }
    }

    // ── Structured Generations ──────────────────────────────────────────

    // @CircuitBreaker(name = "aiService", fallbackMethod = "generateSummaryFallback")
    @Retry(name = "aiService")
    public SummaryResponse generateSummary(String text) {
        metricsService.recordRequest();
        long startTime = System.currentTimeMillis();
        String prompt = promptManager.getSummaryPrompt() + "\n\nText:\n" + text;

        try {
            String rawJson = generate(prompt, Map.of("format", "json", "timeoutMs", 240000));
            LOG.info("RAW AI RESPONSE (Summary):\n{}", rawJson);
            try {
                java.nio.file.Files.writeString(java.nio.file.Path.of("debug-summary.json"), rawJson);
            } catch (Exception ex) {
                LOG.warn("Failed to write debug-summary.json: {}", ex.getMessage());
            }
            String json = responseValidator.extractJson(rawJson);
            String cleaned = json.trim();
            if (!cleaned.endsWith("}") && !cleaned.endsWith("]")) {
                throw new AIServiceException("Incomplete JSON response from AI");
            }
            SummaryResponse summaryResponse = objectMapper.readValue(cleaned, SummaryResponse.class);
            metricsService.recordFeatureSuccess("summary");
            metricsRegistry.recordGenerationSuccess("summary");
            return summaryResponse;
        } catch (Exception e) {
            metricsRegistry.recordGenerationFailure("summary");
            LOG.warn("Failed to parse structured summary JSON, falling back to text-only: {}", e.getMessage());
            // Safe fallback logic inside service layer
            throw new AIServiceException("Failed to generate structured summary", e);
        }
    }

    // @CircuitBreaker(name = "aiService", fallbackMethod = "generateFlashcardsFallback")
    @Retry(name = "aiService")
    public FlashcardsResponse generateFlashcards(String text) {
        metricsService.recordRequest();
        long startTime = System.currentTimeMillis();
        String prompt = promptManager.getFlashcardPrompt() + "\n\nText:\n" + text;

        try {
            String rawJson = generate(prompt, Map.of("format", "json", "timeoutMs", 240000));
            LOG.info("RAW AI RESPONSE (Flashcards):\n{}", rawJson);
            try {
                java.nio.file.Files.writeString(java.nio.file.Path.of("debug-flashcards.json"), rawJson);
            } catch (Exception ex) {
                LOG.warn("Failed to write debug-flashcards.json: {}", ex.getMessage());
            }
            String json = responseValidator.extractJson(rawJson).trim();
            if (json.startsWith("[")) {
                json = "{\"flashcards\":" + json + "}";
            }
            String cleaned = json.trim();
            if (!cleaned.endsWith("}") && !cleaned.endsWith("]")) {
                throw new AIServiceException("Incomplete JSON response from AI");
            }
            FlashcardsResponse response = objectMapper.readValue(cleaned, FlashcardsResponse.class);
            metricsService.recordFeatureSuccess("flashcards");
            metricsRegistry.recordGenerationSuccess("flashcards");
            return response;
        } catch (Exception e) {
            metricsRegistry.recordGenerationFailure("flashcards");
            LOG.error("Failed to parse flashcards JSON", e);
            throw new AIServiceException("Failed to parse flashcards JSON", e);
        }
    }

    // @CircuitBreaker(name = "aiService", fallbackMethod = "generateQuizFallback")
    @Retry(name = "aiService")
    public QuizResponse generateQuiz(String text) {
        metricsService.recordRequest();
        long startTime = System.currentTimeMillis();
        String prompt = promptManager.getQuizPrompt() + "\n\nText:\n" + text;

        try {
            String rawJson = generate(prompt, Map.of("format", "json", "timeoutMs", 240000));
            LOG.info("RAW AI RESPONSE (Quiz):\n{}", rawJson);
            
            // Detailed logging: response length
            LOG.info("QUIZ_RAW_RESPONSE_LENGTH={}", rawJson.length());

            try {
                java.nio.file.Files.writeString(java.nio.file.Path.of("debug-quiz.json"), rawJson);
            } catch (Exception ex) {
                LOG.warn("Failed to write debug-quiz.json: {}", ex.getMessage());
            }
            String json = responseValidator.extractJson(rawJson).trim();
            if (json.startsWith("[")) {
                json = "{\"questions\":" + json + "}";
            }
            String cleaned = json.trim();
            if (!cleaned.endsWith("}") && !cleaned.endsWith("]")) {
                throw new AIServiceException("Incomplete JSON response from AI");
            }

            com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(cleaned);
            com.fasterxml.jackson.databind.JsonNode questionsNode = null;
            if (rootNode.has("questions")) {
                questionsNode = rootNode.get("questions");
            } else if (rootNode.has("quiz")) {
                questionsNode = rootNode.get("quiz");
            } else if (rootNode.has("items")) {
                questionsNode = rootNode.get("items");
            } else if (rootNode.isArray()) {
                questionsNode = rootNode;
            }

            java.util.List<QuizQuestionDTO> validQuestions = new java.util.ArrayList<>();
            if (questionsNode != null && questionsNode.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode qNode : questionsNode) {
                    try {
                        // Locate correctAnswer field or its alias
                        String answerField = null;
                        if (qNode.has("correctAnswer")) answerField = "correctAnswer";
                        else if (qNode.has("correct_answer")) answerField = "correct_answer";
                        else if (qNode.has("correct")) answerField = "correct";

                        if (answerField == null) {
                            LOG.warn("QUIZ_INVALID_QUESTION: missing correctAnswer field");
                            continue;
                        }

                        com.fasterxml.jackson.databind.JsonNode ansNode = qNode.get(answerField);
                        if (ansNode == null) {
                            LOG.warn("QUIZ_INVALID_QUESTION: correctAnswer is null");
                            continue;
                        }

                        String rawAnsValue = "";

                        // Sanitization: Convert array value to String value
                        if (ansNode.isArray()) {
                            if (ansNode.isEmpty()) {
                                LOG.warn("QUIZ_INVALID_ANSWER_FORMAT=empty array");
                                continue;
                            }
                            com.fasterxml.jackson.databind.JsonNode firstElem = ansNode.get(0);
                            String firstVal = firstElem != null ? firstElem.asText() : "";
                            
                            // Log warning when sanitization occurs
                            LOG.warn("QUIZ_SANITISED_ARRAY_ANSWER: converted array {} to single string '{}'", ansNode, firstVal);
                            
                            // Mutate node in place
                            if (qNode instanceof com.fasterxml.jackson.databind.node.ObjectNode) {
                                ((com.fasterxml.jackson.databind.node.ObjectNode) qNode).put(answerField, firstVal);
                            }
                            rawAnsValue = firstVal;
                        } else {
                            rawAnsValue = ansNode.asText();
                        }

                        // Validation: correctAnswer must be non-null and non-blank
                        if (rawAnsValue == null || rawAnsValue.isBlank()) {
                            LOG.warn("QUIZ_INVALID_ANSWER_FORMAT=null or blank");
                            continue;
                        }

                        String trimmedAns = rawAnsValue.trim();

                        // Verify Options array exists and has exactly 4 options
                        com.fasterxml.jackson.databind.JsonNode optionsNode = null;
                        if (qNode.has("options")) optionsNode = qNode.get("options");
                        else if (qNode.has("choices")) optionsNode = qNode.get("choices");
                        else if (qNode.has("answers")) optionsNode = qNode.get("answers");

                        if (optionsNode == null || !optionsNode.isArray() || optionsNode.size() != 4) {
                            LOG.warn("QUIZ_INVALID_QUESTION: options size must be 4");
                            continue;
                        }

                        // Validation: correctAnswer must exist in the options list
                        boolean foundInOptions = false;
                        for (com.fasterxml.jackson.databind.JsonNode optElem : optionsNode) {
                            if (trimmedAns.equalsIgnoreCase(optElem.asText().trim())) {
                                foundInOptions = true;
                                break;
                            }
                        }
                        if (!foundInOptions) {
                            LOG.warn("QUIZ_INVALID_ANSWER_FORMAT={}", rawAnsValue);
                            continue;
                        }

                        // Deserialize this DTO safely
                        QuizQuestionDTO dto = objectMapper.treeToValue(qNode, QuizQuestionDTO.class);
                        validQuestions.add(dto);

                    } catch (Exception qEx) {
                        LOG.error("Failed to parse individual quiz question node safely: {}", qNode, qEx);
                    }
                }
            }

            QuizResponse response = new QuizResponse(validQuestions);
            metricsService.recordFeatureSuccess("quiz");
            metricsRegistry.recordGenerationSuccess("quiz");
            return response;
        } catch (Exception e) {
            metricsRegistry.recordGenerationFailure("quiz");
            LOG.error("Failed to parse quiz JSON", e);
            throw new AIServiceException("Failed to parse quiz JSON", e);
        }
    }

    // @CircuitBreaker(name = "aiService", fallbackMethod = "generateMindMapFallback")
    @Retry(name = "aiService")
    public MindMapResponse generateMindMap(String text) {
        metricsService.recordRequest();
        long startTime = System.currentTimeMillis();
        String prompt = promptManager.getMindmapPrompt() + "\n\nText:\n" + text;

        try {
            String rawJson = generate(prompt, Map.of("format", "json", "timeoutMs", 240000));
            LOG.info("RAW AI RESPONSE (MindMap):\n{}", rawJson);
            try {
                java.nio.file.Files.writeString(java.nio.file.Path.of("debug-mindmap.json"), rawJson);
            } catch (Exception ex) {
                LOG.warn("Failed to write debug-mindmap.json: {}", ex.getMessage());
            }
            metricsService.recordFeatureSuccess("mindmaps");
            metricsRegistry.recordGenerationSuccess("mindmap");
            return new MindMapResponse(rawJson);
        } catch (Exception e) {
            metricsRegistry.recordGenerationFailure("mindmap");
            LOG.error("Failed to generate mindmap", e);
            throw new AIServiceException("Failed to generate mindmap", e);
        }
    }

    // @CircuitBreaker(name = "aiService", fallbackMethod = "chatFallback")
    @Retry(name = "aiService")
    public ChatResponse chat(String question, String context) {
        metricsService.recordRequest();
        long startTime = System.currentTimeMillis();
        String prompt = promptManager.getChatPrompt() + "\n\nContext:\n" + context + "\n\nQuestion:\n" + question;

        try {
            String response = generate(prompt, Map.of());
            return new ChatResponse(response.trim());
        } catch (Exception e) {
            LOG.error("Failed in chat", e);
            throw new AIServiceException("Chat response failed", e);
        }
    }

    // ── Fallback Methods ──────────────────────────────────────────────────

    public String generateFallback(String prompt, Map<String, Object> options, Throwable t) {
        LOG.warn("AI generate() fallback triggered: {}", t.getMessage());
        throw new AIServiceException("AI service unavailable: " + t.getMessage(), t);
    }

    public SummaryResponse generateSummaryFallback(String text, Throwable t) {
        LOG.warn("AI generateSummary() fallback triggered: {}", t.getMessage());
        throw new AIServiceException("AI summary generation unavailable: " + t.getMessage(), t);
    }

    public FlashcardsResponse generateFlashcardsFallback(String text, Throwable t) {
        LOG.warn("AI generateFlashcards() fallback triggered: {}", t.getMessage());
        throw new AIServiceException("AI flashcard generation unavailable: " + t.getMessage(), t);
    }

    public QuizResponse generateQuizFallback(String text, Throwable t) {
        LOG.warn("AI generateQuiz() fallback triggered: {}", t.getMessage());
        throw new AIServiceException("AI quiz generation unavailable: " + t.getMessage(), t);
    }

    public MindMapResponse generateMindMapFallback(String text, Throwable t) {
        LOG.warn("AI generateMindMap() fallback triggered: {}", t.getMessage());
        throw new AIServiceException("AI mindmap generation unavailable: " + t.getMessage(), t);
    }

    public ChatResponse chatFallback(String question, String context, Throwable t) {
        LOG.warn("AI chat() fallback triggered: {}", t.getMessage());
        throw new AIServiceException("AI chat response unavailable: " + t.getMessage(), t);
    }
}
