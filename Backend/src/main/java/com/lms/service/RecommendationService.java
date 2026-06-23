package com.lms.service;

import com.lms.model.TopicMastery;
import com.lms.repository.TopicMasteryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.model.RecommendationResponse;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Recommendation engine — confidence + trend + mastery aware.
 *
 * Converted from Vert.x version:
 *   - Removed io.vertx.core.Future — returns Map<String,Object> directly
 *   - Removed io.vertx.core.json.JsonObject / JsonArray — uses List/Map
 *   - @Service added for Spring DI, constructor injection
 *   - All recommendation logic PRESERVED EXACTLY:
 *       - Risk profiling (HIGH / MEDIUM / LOW)
 *       - Trend-aware action builder
 *       - System insight builder
 *       - Mastery snapshot with levelDelta + risk
 */
@Service
public class RecommendationService {

    private static final Logger LOG = LoggerFactory.getLogger(RecommendationService.class);

    private static final double WEAK_CONFIDENCE_THRESHOLD = 0.65;
    private static final int    MAX_WEAK_TOPICS           = 3;

    private final TopicMasteryRepository masteryRepo;
    private final com.lms.service.ai.AIOrchestratorService aiOrchestratorService;
    private final ObjectMapper objectMapper;

    @Autowired
    public RecommendationService(TopicMasteryRepository masteryRepo,
                                 com.lms.service.ai.AIOrchestratorService aiOrchestratorService,
                                 ObjectMapper objectMapper) {
        this.masteryRepo = masteryRepo;
        this.aiOrchestratorService = aiOrchestratorService;
        this.objectMapper = objectMapper;
    }

    // ── Main recommendation method ────────────────────────────────────────

    public RecommendationResponse getPersonalizedRecommendations(String studentId) {
        Map<String, Object> raw = recommend(studentId);
        
        RecommendationResponse response = new RecommendationResponse();
        
        @SuppressWarnings("unchecked")
        List<String> weakTopicsList = (List<String>) raw.get("weakTopics");
        response.setWeakTopics(weakTopicsList);
        
        response.setSystemInsight(raw);

        // Generate dynamic recommendations using LLM
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an expert AI tutor. Generate personalized learning recommendations based on the following student data:\n");
        prompt.append("Weak Topics: ").append(weakTopicsList != null ? String.join(", ", weakTopicsList) : "None").append("\n");
        prompt.append("System Insight: ").append(raw.get("systemInsight")).append("\n\n");
        prompt.append("OUTPUT FORMAT:\n");
        prompt.append("You MUST respond ONLY with valid JSON in the following format. Do not include any additional text or explanations.\n");
        prompt.append("{\n");
        prompt.append("  \"flashcards\": [\"Review Flashcards for [Topic]\"],\n");
        prompt.append("  \"quizzes\": [\"Take a Quiz on [Topic]\"],\n");
        prompt.append("  \"summaries\": [\"Read summary for [Topic]\"],\n");
        prompt.append("  \"mindMaps\": [\"Explore Mind Map for [Topic]\"],\n");
        prompt.append("  \"studySessions\": [\"Schedule 30min Study Session for [Topic]\"]\n");
        prompt.append("}");

        try {
            String jsonResponse = aiOrchestratorService.generate(prompt.toString(), Map.of());
            if (jsonResponse.contains("```json")) {
                jsonResponse = jsonResponse.substring(jsonResponse.indexOf("```json") + 7);
                if (jsonResponse.contains("```")) {
                    jsonResponse = jsonResponse.substring(0, jsonResponse.indexOf("```"));
                }
            } else if (jsonResponse.contains("```")) {
                jsonResponse = jsonResponse.substring(jsonResponse.indexOf("```") + 3);
                if (jsonResponse.contains("```")) {
                    jsonResponse = jsonResponse.substring(0, jsonResponse.indexOf("```"));
                }
            }
            jsonResponse = jsonResponse.trim();

            Map<String, List<String>> recs = objectMapper.readValue(jsonResponse, new TypeReference<Map<String, List<String>>>() {});
            response.setRecommendedFlashcards(recs.getOrDefault("flashcards", new ArrayList<>()));
            response.setRecommendedQuizzes(recs.getOrDefault("quizzes", new ArrayList<>()));
            response.setRecommendedSummaries(recs.getOrDefault("summaries", new ArrayList<>()));
            response.setRecommendedMindMaps(recs.getOrDefault("mindMaps", new ArrayList<>()));
            response.setRecommendedStudySessions(recs.getOrDefault("studySessions", new ArrayList<>()));
        } catch (Exception e) {
            LOG.error("Failed to generate dynamic recommendations, falling back to defaults", e);
            
            // Fallback
            List<String> flashcards = new ArrayList<>();
            List<String> quizzes = new ArrayList<>();
            List<String> summaries = new ArrayList<>();
            List<String> mindMaps = new ArrayList<>();
            List<String> studySessions = new ArrayList<>();
            
            if (weakTopicsList != null && !weakTopicsList.isEmpty()) {
                for (String wt : weakTopicsList) {
                    flashcards.add("Review Flashcards for " + wt);
                    quizzes.add("Take a Quiz on " + wt);
                    summaries.add("Read summary for " + wt);
                    mindMaps.add("Explore Mind Map for " + wt);
                    studySessions.add("Schedule 30min Study Session for " + wt);
                }
            } else {
                flashcards.add("General Review Flashcards");
                quizzes.add("Mixed Topics Challenge Quiz");
                summaries.add("Weekly Overview Summary");
                mindMaps.add("Course Overview Mind Map");
                studySessions.add("Schedule 1hr General Study Session");
            }
            
            response.setRecommendedFlashcards(flashcards);
            response.setRecommendedQuizzes(quizzes);
            response.setRecommendedSummaries(summaries);
            response.setRecommendedMindMaps(mindMaps);
            response.setRecommendedStudySessions(studySessions);
        }
        
        return response;
    }

    @Cacheable(value = "recommendations", key = "#studentId")
    public Map<String, Object> recommend(String studentId) {

        List<TopicMastery> masteryList = masteryRepo.findByStudentId(studentId);

        if (masteryList.isEmpty()) {
            LOG.info("RECOMMEND no data yet for student={}", studentId);

            List<Map<String, Object>> actions = new ArrayList<>();
            Map<String, Object> defaultAction = new HashMap<>();
            defaultAction.put("topic",    "general");
            defaultAction.put("action",   "Start your first quiz to get personalised recommendations");
            defaultAction.put("priority", "normal");
            actions.add(defaultAction);

            Map<String, Object> result = new HashMap<>();
            result.put("weakTopics",         new ArrayList<>());
            result.put("recommendedActions", actions);
            result.put("systemInsight",      "No quiz data yet — take your first test to unlock recommendations.");
            result.put("masterySnapshot",    new ArrayList<>());
            return result;
        }

        // Sort by confidence ascending (weakest first)
        List<TopicMastery> sorted = masteryList.stream()
                .sorted(Comparator.comparingDouble(TopicMastery::getConfidenceScore))
                .collect(Collectors.toList());

        long decliningCount = sorted.stream().filter(m -> "declining".equals(m.getTrend())).count();
        long improvingCount = sorted.stream().filter(m -> "improving".equals(m.getTrend())).count();
        long masteredCount  = sorted.stream().filter(m -> m.getConfidenceScore() > 0.80).count();

        // Identify weak topics
        List<TopicMastery> weak = sorted.stream()
                .filter(m -> m.getConfidenceScore() < WEAK_CONFIDENCE_THRESHOLD
                          || "declining".equals(m.getTrend()))
                .limit(MAX_WEAK_TOPICS)
                .collect(Collectors.toList());

        List<String>             weakTopics         = new ArrayList<>();
        List<Map<String, Object>> recommendedActions = new ArrayList<>();

        for (TopicMastery m : weak) {
            weakTopics.add(m.getTopic());
            RiskProfile risk = assessRisk(m);

            Map<String, Object> action = new HashMap<>();
            action.put("topic",      m.getTopic());
            action.put("action",     buildAction(m));
            action.put("priority",   buildPriority(risk));
            action.put("risk",       risk.level().name().toLowerCase());
            action.put("riskReason", risk.reason());
            recommendedActions.add(action);
        }

        if (weak.isEmpty()) {
            Map<String, Object> a1 = new HashMap<>();
            a1.put("topic", "all"); a1.put("action", "Great progress! Push to harder difficulty levels."); a1.put("priority", "normal");
            recommendedActions.add(a1);
            if (improvingCount > 0) {
                Map<String, Object> a2 = new HashMap<>();
                a2.put("topic", "all"); a2.put("action", "Try mixed-topic quizzes — you're on an upward trend."); a2.put("priority", "normal");
                recommendedActions.add(a2);
            }
        }

        long highRiskCount   = countHighRisk(sorted);
        String systemInsight = buildSystemInsight(decliningCount, improvingCount,
                masteredCount, sorted.size(), highRiskCount);

        // Full mastery snapshot
        List<Map<String, Object>> snapshot = new ArrayList<>();
        for (TopicMastery m : sorted) {
            RiskProfile risk = assessRisk(m);
            Map<String, Object> entry = new HashMap<>();
            entry.put("topic",          m.getTopic());
            entry.put("accuracy",       m.getAccuracy());
            entry.put("confidence",     m.getConfidenceScore());
            entry.put("trend",          m.getTrend());
            entry.put("tier",           m.targetDifficulty().name());
            entry.put("levelDelta",     m.getLevelDelta());
            entry.put("totalQuestions", m.getTotalQuestions());
            entry.put("risk",           risk.level().name().toLowerCase());
            entry.put("riskReason",     risk.reason());
            snapshot.add(entry);
        }

        LOG.info("RECOMMEND student={} weakCount={} declining={} improving={} highRisk={}",
                studentId, weak.size(), decliningCount, improvingCount, highRiskCount);

        Map<String, Object> result = new HashMap<>();
        result.put("weakTopics",         weakTopics);
        result.put("recommendedActions", recommendedActions);
        result.put("systemInsight",      systemInsight);
        result.put("masterySnapshot",    snapshot);
        return result;
    }

    // ── Risk profiling (PRESERVED EXACTLY) ───────────────────────────────

    private enum RiskLevel { HIGH, MEDIUM, LOW }

    private record RiskProfile(RiskLevel level, String reason) {}

    private RiskProfile assessRisk(TopicMastery m) {
        double confidence   = m.getConfidenceScore();
        double accuracy     = m.getAccuracy();
        double prevAccuracy = m.getPreviousAccuracy();
        String trend        = m.getTrend();

        double accuracyDrop = prevAccuracy - accuracy;
        if (accuracyDrop > 0.15) {
            return new RiskProfile(RiskLevel.HIGH,
                    "Rapid accuracy drop detected (" + String.format("%.0f%%", accuracyDrop * 100) +
                    " decline) — learning regression risk");
        }

        if (confidence < 0.60 && "declining".equals(trend)) {
            return new RiskProfile(RiskLevel.HIGH,
                    "Low confidence (" + String.format("%.0f%%", confidence * 100) +
                    "%) with declining trend — high failure risk");
        }
        if (confidence < 0.60 || "declining".equals(trend)) {
            String reason = confidence < 0.60
                    ? "Below confidence threshold (" + String.format("%.0f%%", confidence * 100) + "%)"
                    : "Accuracy is declining — may drop below threshold soon";
            return new RiskProfile(RiskLevel.MEDIUM, reason);
        }
        return new RiskProfile(RiskLevel.LOW, "On track");
    }

    private long countHighRisk(List<TopicMastery> list) {
        return list.stream().filter(m -> assessRisk(m).level() == RiskLevel.HIGH).count();
    }

    // ── Action builder (PRESERVED EXACTLY) ───────────────────────────────

    private String buildAction(TopicMastery m) {
        double confidence = m.getConfidenceScore();
        String topic      = m.getTopic();
        String trend      = m.getTrend();

        if ("declining".equals(trend) && confidence < 0.40)
            return "Urgent: " + topic + " accuracy is falling — restart from Level 1 basics";
        if ("declining".equals(trend))
            return "Reinforce " + topic + " — your accuracy is declining. Revisit recent mistakes";
        if (confidence < 0.40)
            return "Revise " + topic + " basics — start at Level 1";
        if (confidence < 0.55)
            return "Take a Level 2 " + topic + " quiz to build confidence";
        if (confidence < 0.65)
            return "Review recent " + topic + " mistakes and retry Level 2";
        return "Challenge yourself with advanced " + topic + " problems";
    }

    private String buildPriority(RiskProfile risk) {
        return switch (risk.level()) {
            case HIGH   -> "urgent";
            case MEDIUM -> "high";
            case LOW    -> "normal";
        };
    }

    // ── System insight builder (PRESERVED EXACTLY) ────────────────────────

    private String buildSystemInsight(long declining, long improving, long mastered,
                                      int total, long highRisk) {
        if (total == 0) return "No data yet.";

        if (highRisk >= 2)
            return highRisk + " topics are at high failure risk (low confidence + declining). Immediate reinforcement required.";
        if (highRisk == 1)
            return "One topic is predicted to fail soon — confidence is low and dropping. Reinforce it now.";
        if (declining >= 2)
            return declining + " topics are declining in accuracy. Prioritise reinforcement before attempting new content.";
        if (declining == 1 && improving == 0)
            return "One topic is losing ground. Focus reinforcement there before advancing others.";
        if (mastered == total)
            return "All topics mastered! Move to advanced problems or cross-topic challenges.";
        if (improving > (long) total / 2)
            return "Strong upward momentum across " + improving + " topics — keep your current pace.";
        if (improving > 0 && declining == 0)
            return "Steady improvement across topics. Continue current learning path.";
        return "Focus on your weakest topics before expanding to new content.";
    }
}
