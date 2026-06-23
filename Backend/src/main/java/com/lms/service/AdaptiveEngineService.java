package com.lms.service;

import com.lms.model.Question;
import com.lms.model.ResponseRecord;
import com.lms.model.TestResponse;
import com.lms.model.UserLearningProfile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AdaptiveEngineService {

    @Autowired
    private PredictionEngine predictionEngine;

    @Autowired
    private UserProfileService userProfileService;

    @Autowired
    private DecisionFeedbackEngine feedbackEngine;

    @Autowired
    private AIService aiService;

    /**
     * 🧠 MAIN ENGINE ENTRY
     */
    public TestResponse selectNextQuestion(
            String userId,
            List<Question> allQuestions,
            List<ResponseRecord> history
    ) {

        // 🛑 Safety checks
        if (allQuestions == null || allQuestions.isEmpty()) {
            return buildResponse(null, "No questions available");
        }

        if (history == null) {
            history = new ArrayList<>();
        }

        // 🔮 1. Prediction Layer
        Map<String, Double> predictionMap =
                predictionEngine.predictFailureProbability(history);

        String predictedWeakConcept =
                predictionEngine.predictNextWeakConcept(predictionMap);

        // 🧠 2. User Profile
        UserLearningProfile profile =
                userProfileService.buildProfile(userId, history);

        // 🛑 SAFE DEFAULT PROFILE
        if (profile == null) {
            profile = new UserLearningProfile();
            profile.setPreferredDifficulty(1);
            profile.setConceptStrength(new HashMap<>());
            profile.setFatigueScore(0.0);
        }

        // 📊 3. Mastery + Level
        double mastery = calculateMastery(history);
        int level = determineLevel(mastery);

        // 🔁 4. Feedback Loop
        double impact = feedbackEngine.evaluateDecisionImpact(history);
        double recentAccuracy = feedbackEngine.calculateRecentAccuracy(history);

        // 🎯 5. Select Best Question
        Question bestQuestion = null;
        double bestScore = Double.MAX_VALUE;

        for (Question q : allQuestions) {

            double score = calculateScore(
                    q,
                    level,
                    predictedWeakConcept,
                    profile,
                    history,
                    recentAccuracy,
                    impact
            );

            // 🤖 AI Adjustment (SAFE)
            try {
                score += aiService.adjustScore(q, history);
            } catch (Exception ignored) {}

            if (score < bestScore) {
                bestScore = score;
                bestQuestion = q;
            }
        }

        if (bestQuestion == null) {
            return buildResponse(null, "No suitable question found");
        }

        // 🧠 6. Explainability
        String reason = buildExplanation(
                bestQuestion,
                level,
                predictedWeakConcept,
                profile,
                recentAccuracy
        );

        return buildResponse(bestQuestion, reason);
    }

    /**
     * 🧠 CORE SCORING LOGIC
     */
    private double calculateScore(
            Question q,
            int level,
            String predictedWeakConcept,
            UserLearningProfile profile,
            List<ResponseRecord> history,
            double recentAccuracy,
            double impact
    ) {

        double score = 0;

        // 🎯 Difficulty alignment
        score += Math.abs(q.getDifficulty() - level) * 2.5;

        // 🔮 Prediction boost
        if (predictedWeakConcept != null &&
                predictedWeakConcept.equals(q.getConcept())) {
            score -= 3.0;
        }

        // 🧠 User preference
        score += Math.abs(q.getDifficulty() - profile.getPreferredDifficulty());

        // ⚠️ Fatigue handling
        if (profile.getFatigueScore() > 0.6) {
            score += 1.5;
        }

        // 🔁 Avoid repetition
        long seenCount = history.stream()
                .filter(r -> r.getQuestionId().equals(q.getId()))
                .count();

        score += seenCount * 1.2;

        // 🧠 Concept strength
        Map<String, Double> conceptMap = profile.getConceptStrength();
        if (conceptMap != null && conceptMap.containsKey(q.getConcept())) {
            double strength = conceptMap.get(q.getConcept());
            if (strength < 0) {
                score -= 2.0;
            } else {
                score += 1.0;
            }
        }

        // 🔁 Feedback tuning
        if (recentAccuracy < 0.4) score -= 2.0;
        if (recentAccuracy > 0.8) score += 1.5;

        // 🧠 Impact adjustment
        if (impact < 0) score -= 1.0;

        return score;
    }

    /**
     * 📊 Mastery Calculation
     */
    private double calculateMastery(List<ResponseRecord> history) {
        if (history == null || history.isEmpty()) return 0.0;

        double correct = history.stream()
                .filter(ResponseRecord::isCorrect)
                .count();

        return correct / history.size();
    }

    /**
     * 🎯 Level Logic
     */
    private int determineLevel(double mastery) {
        if (mastery < 0.3) return 1;
        if (mastery < 0.6) return 2;
        if (mastery < 0.8) return 3;
        return 4;
    }

    /**
     * 🧠 Explainability
     */
    private String buildExplanation(
            Question q,
            int level,
            String predictedWeakConcept,
            UserLearningProfile profile,
            double recentAccuracy
    ) {

        if (predictedWeakConcept != null &&
                predictedWeakConcept.equals(q.getConcept())) {
            return "Selected to improve weak concept: " + q.getConcept();
        }

        if (profile.getFatigueScore() > 0.6) {
            return "Selected easier question due to fatigue";
        }

        if (recentAccuracy < 0.4) {
            return "Difficulty reduced due to low recent accuracy";
        }

        if (recentAccuracy > 0.8) {
            return "Difficulty increased due to strong performance";
        }

        return "Selected based on adaptive difficulty level " + level;
    }

    /**
     * 📦 Response Builder
     */
    private TestResponse buildResponse(Question q, String reason) {
        return new TestResponse(q, reason);
    }
}