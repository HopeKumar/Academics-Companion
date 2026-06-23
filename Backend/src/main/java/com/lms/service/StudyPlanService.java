package com.lms.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.model.RecommendationResponse;
import com.lms.model.StudentLearningProfile;
import com.lms.model.StudyPlan;
import com.lms.repository.StudyPlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class StudyPlanService {

    private static final Logger LOG = LoggerFactory.getLogger(StudyPlanService.class);

    private final StudyPlanRepository studyPlanRepository;
    private final AdaptiveLearningService adaptiveLearningService;
    private final RecommendationService recommendationService;
    private final RetrievalService retrievalService;
    private final ContextBuilder contextBuilder;
    private final com.lms.service.ai.AIOrchestratorService aiOrchestratorService;
    private final ObjectMapper objectMapper;

    @Autowired
    public StudyPlanService(StudyPlanRepository studyPlanRepository,
                            AdaptiveLearningService adaptiveLearningService,
                            RecommendationService recommendationService,
                            RetrievalService retrievalService,
                            ContextBuilder contextBuilder,
                            com.lms.service.ai.AIOrchestratorService aiOrchestratorService,
                            ObjectMapper objectMapper) {
        this.studyPlanRepository = studyPlanRepository;
        this.adaptiveLearningService = adaptiveLearningService;
        this.recommendationService = recommendationService;
        this.retrievalService = retrievalService;
        this.contextBuilder = contextBuilder;
        this.aiOrchestratorService = aiOrchestratorService;
        this.objectMapper = objectMapper;
    }

    public StudyPlan generateStudyPlan(String userId, String availableTime) {
        LOG.info("Generating study plan for user: {} with available time: {}", userId, availableTime);

        StudentLearningProfile profile = adaptiveLearningService.getProfile(userId);
        RecommendationResponse recommendations = recommendationService.getPersonalizedRecommendations(userId);

        List<String> weakTopics = recommendations.getWeakTopics();
        String currentLevel = profile.getCurrentLevel().name();
        double masteryScore = profile.getOverallMastery();

        String contextText = "";
        if (weakTopics != null && !weakTopics.isEmpty()) {
            List<com.lms.model.DocumentChunk> allChunks = new ArrayList<>();
            for (String topic : weakTopics) {
                allChunks.addAll(retrievalService.retrieve(userId, topic, null, 2));
            }
            if (!allChunks.isEmpty()) {
                contextText = contextBuilder.buildContext(allChunks).getContextText();
            }
        }

        String prompt = buildPrompt(currentLevel, masteryScore, weakTopics, availableTime, contextText);

        Map<String, Object> options = new HashMap<>();
        String jsonResponse = aiOrchestratorService.generate(prompt, options);

        try {
            // Check if response has markdown JSON block
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

            Map<String, Object> parsedResponse = objectMapper.readValue(jsonResponse, new TypeReference<Map<String, Object>>() {});

            StudyPlan plan = studyPlanRepository.findByUserId(userId).orElse(new StudyPlan());
            plan.setUserId(userId);
            plan.setAvailableTime(availableTime);
            plan.setGeneratedAt(Instant.now().toString());

            if (parsedResponse.containsKey("dailyPlan")) {
                plan.setDailyPlan((List<Map<String, Object>>) parsedResponse.get("dailyPlan"));
            }
            if (parsedResponse.containsKey("weeklyPlan")) {
                plan.setWeeklyPlan((List<Map<String, Object>>) parsedResponse.get("weeklyPlan"));
            }

            return studyPlanRepository.save(plan);

        } catch (Exception e) {
            LOG.error("Failed to parse LLM study plan response: {}", e.getMessage());
            throw new RuntimeException("Failed to parse study plan from AI. Raw response: " + jsonResponse, e);
        }
    }

    public StudyPlan getStudyPlan(String userId) {
        return studyPlanRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Study plan not found for user"));
    }

    public StudyPlan regenerateStudyPlan(String userId, String availableTime) {
        return generateStudyPlan(userId, availableTime);
    }

    private String buildPrompt(String currentLevel, double masteryScore, List<String> weakTopics, String availableTime, String contextText) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an AI study planner for an e-learning application.\n");
        prompt.append("Given the following student context:\n");
        prompt.append("- Current Level: ").append(currentLevel).append("\n");
        prompt.append("- Overall Mastery Score: ").append(masteryScore).append("\n");
        prompt.append("- Weak Topics: ").append(weakTopics != null && !weakTopics.isEmpty() ? String.join(", ", weakTopics) : "None").append("\n");
        prompt.append("- Available Study Time: ").append(availableTime).append("\n\n");
        
        if (contextText != null && !contextText.isEmpty()) {
            prompt.append("Use the following actual course content to generate specific tasks (e.g. \"Read Chapter 2 on AI\"): \n");
            prompt.append(contextText).append("\n\n");
        }
        
        prompt.append("Generate a structured Daily Plan and Weekly Plan for this student. Focus the plans on improving their weak topics while reinforcing general knowledge through a mix of Flashcards, Quizzes, Summaries, and Mind Maps.\n\n");
        prompt.append("OUTPUT FORMAT:\n");
        prompt.append("You MUST respond ONLY with valid JSON in the following format. Do not include any additional text or explanations.\n");
        prompt.append("{\n");
        prompt.append("  \"dailyPlan\": [\n");
        prompt.append("    { \"day\": \"Day 1\", \"tasks\": [\"Task 1\", \"Task 2\"] },\n");
        prompt.append("    { \"day\": \"Day 2\", \"tasks\": [\"Task 1\", \"Task 2\"] }\n");
        prompt.append("  ],\n");
        prompt.append("  \"weeklyPlan\": [\n");
        prompt.append("    { \"week\": \"Week 1\", \"goals\": [\"Goal 1\", \"Goal 2\"] }\n");
        prompt.append("  ]\n");
        prompt.append("}");
        
        return prompt.toString();
    }
}
