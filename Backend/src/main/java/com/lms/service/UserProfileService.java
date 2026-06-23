package com.lms.service;

import com.lms.model.ResponseRecord;
import com.lms.model.UserLearningProfile;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a real-time learning profile from a user's answer history.
 *
 * Tracks: learning speed, retention rate, preferred difficulty,
 * per-concept strengths, and fatigue score.
 *
 * All logic PRESERVED EXACTLY from original.
 */
@Service
public class UserProfileService {

    /**
     * Build or update profile from answer history.
     */
    public UserLearningProfile buildProfile(String userId, List<ResponseRecord> history) {

        UserLearningProfile profile = new UserLearningProfile();
        profile.setUserId(userId);

        if (history == null || history.isEmpty()) {
            return profile;
        }

        profile.setLearningSpeed(calculateLearningSpeed(history));
        profile.setRetentionRate(calculateRetention(history));
        profile.setPreferredDifficulty(calculatePreferredDifficulty(history));
        profile.setConceptStrength(analyzeConceptStrength(history));
        profile.setRecentAttempts(history.size());
        profile.setFatigueScore(history.size() > 10 ? 0.7 : 0.2);

        return profile;
    }

    // ── Private computation methods (PRESERVED EXACTLY) ───────────────────

    private double calculateLearningSpeed(List<ResponseRecord> history) {
        int correct = (int) history.stream().filter(ResponseRecord::isCorrect).count();
        return (double) correct / history.size();
    }

    private double calculateRetention(List<ResponseRecord> history) {
        return history.stream()
                .mapToDouble(r -> r.isCorrect() ? 1 : 0)
                .average()
                .orElse(0);
    }

    private int calculatePreferredDifficulty(List<ResponseRecord> history) {
        return (int) history.stream()
                .mapToInt(ResponseRecord::getDifficulty)
                .average()
                .orElse(1);
    }

    private Map<String, Double> analyzeConceptStrength(List<ResponseRecord> history) {
        Map<String, Double> map = new HashMap<>();
        for (ResponseRecord r : history) {
            double score = r.isCorrect() ? 1 : -1;
            map.put(r.getConcept(), map.getOrDefault(r.getConcept(), 0.0) + score);
        }
        return map;
    }
}
