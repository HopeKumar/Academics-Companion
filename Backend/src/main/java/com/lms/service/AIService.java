package com.lms.service;

import com.lms.config.AppConfig;
import com.lms.metrics.MetricsRegistry;
import com.lms.model.EvalResult;
import com.lms.model.Question;
import com.lms.model.ResponseRecord;
import com.lms.util.InMemoryCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * AI evaluation service backed by Mistral — SAFE MODE.
 *
 * Changes from original:
 *   - Moved from com.lms.ai → com.lms.service (package cleaned)
 *   - RedisCache replaced with InMemoryCache (no external deps)
 *   - adjustScore() now precomputes per-concept maps (single pass)
 *     instead of two repeated stream operations
 *   - All AI calls wrapped in try/catch — never crashes the system
 *   - All intelligence logic PRESERVED EXACTLY
 */
import com.lms.service.ai.AIOrchestratorService;

@Service
public class AIService {

    private static final Logger LOG = LoggerFactory.getLogger(AIService.class);

    private final AIOrchestratorService aiOrchestratorService;
    private final Semaphore      semaphore = new Semaphore(AppConfig.AI_CONCURRENCY, true);
    private final InMemoryCache  cache;
    private final MetricsRegistry metrics;
    private final com.lms.service.ai.provider.PromptManager promptManager;

    @Autowired
    public AIService(InMemoryCache cache, MetricsRegistry metrics, AIOrchestratorService aiOrchestratorService, com.lms.service.ai.provider.PromptManager promptManager) {
        this.cache       = cache;
        this.metrics     = metrics;
        this.aiOrchestratorService = aiOrchestratorService;
        this.promptManager = promptManager;
    }

    // ── Public: evaluate answer ───────────────────────────────────────────

    public void evaluate(String question, String correctAnswer, String studentAnswer,
                         Consumer<EvalResult> callback) {
        try {
            long   startMs = System.currentTimeMillis();
            String key     = buildCacheKey(question, correctAnswer, studentAnswer);

            cache.get(key, cached -> {
                if (cached != null) {
                    try {
                        boolean correct     = cached.contains("\"correct\":true");
                        String  explanation = extractJsonString(cached, "explanation");
                        EvalResult hit = new EvalResult(correct, explanation, true);
                        metrics.recordAiCall(System.currentTimeMillis() - startMs, true);
                        LOG.debug("AI CACHE HIT key={}", abbrev(key));
                        callback.accept(hit);
                    } catch (Exception e) {
                        LOG.warn("AI CACHE PARSE ERROR — falling through: {}", e.getMessage());
                        callWithSemaphore(key, question, correctAnswer, studentAnswer, startMs, callback);
                    }
                    return;
                }
                callWithSemaphore(key, question, correctAnswer, studentAnswer, startMs, callback);
            });
        } catch (Exception e) {
            LOG.error("AI evaluate() unexpected error — returning safe fallback: {}", e.getMessage());
            callback.accept(new EvalResult(false, "AI evaluation unavailable. Please retry.", false));
        }
    }

    /**
     * Score adjustment hook used by AdaptiveEngineService.
     *
     * PERFORMANCE FIX: original used two separate stream passes over history.
     * Now uses a single pass to build precomputed maps, then reads from them.
     *
     * Returns a small delta (positive = harder, negative = easier).
     */
    public double adjustScore(Question q, List<ResponseRecord> history) {
        try {
            if (history == null || history.isEmpty()) return 0.0;

            // Single-pass: build seen count and correct count per concept
            Map<String, long[]> conceptStats = history.stream()
                    .filter(r -> r.getConcept() != null)
                    .collect(Collectors.groupingBy(
                            ResponseRecord::getConcept,
                            Collectors.collectingAndThen(
                                    Collectors.toList(),
                                    list -> new long[]{
                                            list.size(),
                                            list.stream().filter(ResponseRecord::isCorrect).count()
                                    }
                            )
                    ));

            long[] stats = conceptStats.get(q.getConcept());
            if (stats == null) return 0.0;

            long seen              = stats[0];
            long correctOnConcept  = stats[1];
            double conceptAccuracy = (double) correctOnConcept / seen;

            if (conceptAccuracy < 0.4 && seen > 2) return -1.5; // struggling → select it
            if (conceptAccuracy > 0.8 && seen > 3) return  1.5; // mastered  → avoid it
            return 0.0;
        } catch (Exception e) {
            LOG.warn("AIService.adjustScore() error — returning 0: {}", e.getMessage());
            return 0.0;
        }
    }

    // ── Private: semaphore gate ───────────────────────────────────────────

    private void callWithSemaphore(String key, String question, String correctAnswer,
                                   String studentAnswer, long startMs,
                                   Consumer<EvalResult> callback) {
        if (!semaphore.tryAcquire()) {
            metrics.recordAiError();
            LOG.warn("AI QUEUE FULL — permits={} concurrency={} — returning busy fallback",
                    semaphore.availablePermits(), AppConfig.AI_CONCURRENCY);
            callback.accept(new EvalResult(false,
                    "Service is busy. Please try again shortly.", false));
            return;
        }
        LOG.info("AI CALL acquired — availablePermits={}", semaphore.availablePermits());
        callMistral(key, question, correctAnswer, studentAnswer, startMs, callback);
    }

    // ── Private: Mistral HTTP call ─────────────────────────────────────────

    private void callMistral(String key, String question, String correctAnswer,
                            String studentAnswer, long startMs,
                            Consumer<EvalResult> callback) {
        try {
            String prompt = promptManager.getEvaluationPrompt(question, correctAnswer, studentAnswer);
            
            // Call resilient LLM Provider
            String raw = aiOrchestratorService.generate(prompt, Map.of());
            semaphore.release();

            long elapsed = System.currentTimeMillis() - startMs;
            boolean isCorrect   = parseVerdict(raw);
            String  explanation = raw.length() > AppConfig.AI_MAX_RESPONSE_CHARS
                    ? raw.substring(0, AppConfig.AI_MAX_RESPONSE_CHARS) + "..."
                    : raw;

            metrics.recordAiCall(elapsed, false);
            LOG.info("AI RESULT correct={} elapsedMs={} cached=false", isCorrect, elapsed);

            EvalResult result = new EvalResult(isCorrect, explanation, false);

            String cacheJson = "{\"correct\":" + isCorrect
                    + ",\"explanation\":\"" + explanation.replace("\"", "'").replace("\n", " ") + "\"}";
            cache.setWithTtl(key, cacheJson, AppConfig.REDIS_AI_EVAL_TTL_SECONDS);

            callback.accept(result);

        } catch (com.lms.exception.AIServiceException e) {
            semaphore.release();
            metrics.recordAiError();
            LOG.warn("AI HTTP ERROR: {}", e.getMessage());
            callback.accept(new EvalResult(false, "AI evaluation unavailable. Please retry.", false));
        } catch (Exception e) {
            semaphore.release();
            metrics.recordAiError();
            LOG.error("AI callMistral() unexpected error: {}", e.getMessage());
            callback.accept(new EvalResult(false, "AI evaluation unavailable. Please retry.", false));
        }
    }

    // ── Verdict parser (PRESERVED EXACTLY) ───────────────────────────────

    private boolean parseVerdict(String response) {
        if (response == null || response.isBlank()) return false;

        String lower     = response.toLowerCase().trim();
        String firstWord = lower.split("[\\s\\n\\r.,!?]+")[0];

        switch (firstWord) {
            case "correct":   return true;
            case "wrong":
            case "incorrect": return false;
            case "yes":       return true;
            case "no":        return false;
        }

        boolean hasCorrect = lower.contains("correct")
                || lower.contains("right answer")
                || lower.contains("is accurate");
        boolean hasWrong   = lower.contains("wrong")
                || lower.contains("incorrect")
                || lower.contains("not correct")
                || lower.contains("not right")
                || lower.contains("is not")
                || lower.contains("isn't");

        if (hasCorrect && !hasWrong) return true;
        if (hasWrong)                return false;

        LOG.warn("AMBIGUOUS AI RESPONSE — defaulting false: {}", abbrev(lower));
        return false;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String buildCacheKey(String question, String correct, String student) {
        return InMemoryCache.buildKey("ai", question, correct, student);
    }

    private String abbrev(String s) {
        return s.length() > 60 ? s.substring(0, 60) + "..." : s;
    }

    private String extractJsonString(String json, String field) {
        String search = "\"" + field + "\":\"";
        int start = json.indexOf(search);
        if (start == -1) return "";
        start += search.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return "";
        return json.substring(start, end);
    }
}
