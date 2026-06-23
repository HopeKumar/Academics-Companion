package com.lms.service;

import com.lms.model.StudentLearningProfile;
import com.lms.model.StudentLevel;
import com.lms.repository.StudentLearningProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AdaptiveLearningService {

    private static final Logger LOG = LoggerFactory.getLogger(AdaptiveLearningService.class);

    private final StudentLearningProfileRepository profileRepo;

    @Autowired
    public AdaptiveLearningService(StudentLearningProfileRepository profileRepo) {
        this.profileRepo = profileRepo;
    }

    public StudentLearningProfile getProfile(String userId) {
        return profileRepo.findByUserId(userId)
                .orElseGet(() -> createDefaultProfile(userId));
    }

    private StudentLearningProfile createDefaultProfile(String userId) {
        StudentLearningProfile profile = new StudentLearningProfile();
        profile.setUserId(userId);
        return profileRepo.save(profile);
    }

    public StudentLearningProfile trackQuizPerformance(String userId, double score) {
        StudentLearningProfile profile = getProfile(userId);
        
        // Update basic stats
        int totalQuizzes = profile.getTotalQuizzes() + 1;
        double newAverage = ((profile.getAverageScore() * profile.getTotalQuizzes()) + score) / totalQuizzes;
        
        profile.setTotalQuizzes(totalQuizzes);
        profile.setAverageScore(newAverage);
        
        updateActivityAndStreak(profile);
        recalculateMasteryAndLevel(profile);
        
        profile.setUpdatedAt(Instant.now().toString());
        return profileRepo.save(profile);
    }

    public void updateActivityAndStreak(StudentLearningProfile profile) {
        Instant now = Instant.now();
        if (profile.getLastActivity() != null) {
            Instant last = Instant.parse(profile.getLastActivity());
            long daysBetween = ChronoUnit.DAYS.between(last, now);
            if (daysBetween == 1) {
                profile.setStreakDays(profile.getStreakDays() + 1);
            } else if (daysBetween > 1) {
                profile.setStreakDays(0);
            }
        } else {
            profile.setStreakDays(1);
        }
        profile.setLastActivity(now.toString());
    }

    public void recalculateMasteryAndLevel(StudentLearningProfile profile) {
        // Mastery Formula: 40% Quiz Accuracy, 20% Topic Consistency, 20% Learning Streak, 10% Revision Frequency, 10% Engagement
        double accuracy = profile.getAverageScore();
        double topicConsistency = profile.getTopicConsistency();
        double learningStreak = Math.min(profile.getStreakDays() * 10.0, 100.0);
        double revisionFrequency = profile.getRevisionFrequency();
        double engagementScore = profile.getEngagementScore();

        double mastery = (0.40 * accuracy) + (0.20 * topicConsistency) + (0.20 * learningStreak) + (0.10 * revisionFrequency) + (0.10 * engagementScore);
        profile.setOverallMastery(mastery);

        if (mastery < 40) {
            profile.setCurrentLevel(StudentLevel.BEGINNER);
        } else if (mastery < 75) {
            profile.setCurrentLevel(StudentLevel.INTERMEDIATE);
        } else {
            profile.setCurrentLevel(StudentLevel.ADVANCED);
        }
    }

    public List<String> getRecommendations(String userId) {
        StudentLearningProfile profile = getProfile(userId);
        List<String> recommendations = new ArrayList<>();
        
        switch (profile.getCurrentLevel()) {
            case BEGINNER:
                recommendations.add("More summaries");
                recommendations.add("More flashcards");
                recommendations.add("Easy quizzes");
                break;
            case INTERMEDIATE:
                recommendations.add("Mixed quizzes");
                recommendations.add("Concept reinforcement");
                break;
            case ADVANCED:
                recommendations.add("Hard questions");
                recommendations.add("Application-based problems");
                recommendations.add("Previous-year questions");
                break;
        }
        return recommendations;
    }

    public void updateTrends(StudentLearningProfile profile) {
        // Knowledge Growth Trend - Add current overall mastery
        List<Double> growthTrend = profile.getKnowledgeGrowthTrend();
        if (growthTrend == null) {
            growthTrend = new ArrayList<>();
        }
        growthTrend.add(profile.getOverallMastery());
        if (growthTrend.size() > 10) {
            growthTrend.remove(0); // keep last 10 points
        }
        profile.setKnowledgeGrowthTrend(growthTrend);

        // Update basic static strings for Subject and Topic mastery trends
        // (This would ideally be wired up to TopicMastery but extended per requirements)
        Map<String, String> subjectTrend = profile.getSubjectMasteryTrend();
        if (subjectTrend == null) subjectTrend = new java.util.HashMap<>();
        subjectTrend.put("General", profile.getOverallMastery() >= 75 ? "improving" : "stable");
        profile.setSubjectMasteryTrend(subjectTrend);

        Map<String, String> topicTrend = profile.getTopicMasteryTrend();
        if (topicTrend == null) topicTrend = new java.util.HashMap<>();
        topicTrend.put("Latest", profile.getCurrentLevel().toString());
        profile.setTopicMasteryTrend(topicTrend);

        profileRepo.save(profile);
    }
}
