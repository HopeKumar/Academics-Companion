package com.lms.service;

import com.lms.config.AppConfig;
import com.lms.model.ResponseRecord;
import com.lms.model.TopicMastery;
import com.lms.repository.ResponseRecordRepository;
import com.lms.repository.TopicMasteryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.lms.model.StudentDashboardResponse;
import com.lms.model.FacultyDashboardResponse;
import com.lms.model.StudentLearningProfile;
import com.lms.model.StudentLevel;
import com.lms.repository.StudentLearningProfileRepository;
import com.lms.repository.UserRepository;

@Service
public class AnalyticsService {

    private static final Logger LOG = LoggerFactory.getLogger(AnalyticsService.class);

    private final ResponseRecordRepository responseRepo;
    private final TopicMasteryRepository    masteryRepo;
    private final com.lms.service.ai.AIOrchestratorService aiOrchestratorService;
    private final org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;
    private final StudentLearningProfileRepository profileRepo;
    private final UserRepository userRepository;

    @Autowired
    public AnalyticsService(ResponseRecordRepository responseRepo,
                            TopicMasteryRepository masteryRepo,
                            com.lms.service.ai.AIOrchestratorService aiOrchestratorService,
                            org.springframework.data.mongodb.core.MongoTemplate mongoTemplate,
                            StudentLearningProfileRepository profileRepo,
                            UserRepository userRepository) {
        this.responseRepo = responseRepo;
        this.masteryRepo  = masteryRepo;
        this.aiOrchestratorService = aiOrchestratorService;
        this.mongoTemplate = mongoTemplate;
        this.profileRepo = profileRepo;
        this.userRepository = userRepository;
    }

    /**
     * Clear analytics cache for a user when they answer a new question to force fresh scores.
     */
    @CacheEvict(value = "analytics", key = "#userId")
    public void invalidateCache(String userId) {
        LOG.info("Evicting analytics cache for user {}", userId);
    }

    /**
     * Compute full dashboard aggregates for a user.
     */
    @Cacheable(value = "analytics", key = "#userId")
    public Map<String, Object> getAnalytics(String userId) {
        return computeAnalytics(userId);
    }

    private Map<String, Object> computeAnalytics(String userId) {
        LOG.info("Computing fresh analytics aggregates for user {}", userId);

        long totalQuestions = responseRepo.countByUserId(userId);
        long correctAnswers = responseRepo.countByUserIdAndCorrectTrue(userId);
        double accuracy = totalQuestions > 0 ? (double) correctAnswers / totalQuestions : 0.0;

        List<TopicMastery> masteries = masteryRepo.findByStudentId(userId);

        int currentStreak = calculateDailyStreak(userId);

        // Group masteries by strength
        List<Map<String, Object>> strengths = new ArrayList<>();
        List<Map<String, Object>> weaknesses = new ArrayList<>();

        for (TopicMastery m : masteries) {
            Map<String, Object> details = Map.of(
                    "topic", m.getTopic(),
                    "accuracy", m.getAccuracy(),
                    "confidence", m.getConfidenceScore(),
                    "trend", m.getTrend(),
                    "tier", m.targetDifficulty().name(),
                    "totalAttempts", m.getTotalQuestions()
            );

            if (m.getConfidenceScore() >= 0.70) {
                strengths.add(details);
            } else if (m.getConfidenceScore() < 0.55 || "declining".equals(m.getTrend())) {
                weaknesses.add(details);
            }
        }

        // Quiz Analytics
        Map<String, Double> topicPerformance = new HashMap<>();
        for (TopicMastery m : masteries) {
            topicPerformance.put(m.getTopic(), m.getAccuracy());
        }

        List<ResponseRecord> recentResponses = responseRepo.findByUserIdOrderByTimestampAsc(userId);
        
        // Difficulty Performance
        Map<Integer, Double> diffCorrect = new HashMap<>();
        Map<Integer, Integer> diffTotal = new HashMap<>();
        
        // Improvement Trend (last 10 buckets of 5 questions, or just last 10 questions)
        List<Double> improvementTrend = new ArrayList<>();
        int bucketSize = 5;
        int correctInBucket = 0;
        int countInBucket = 0;

        for (ResponseRecord r : recentResponses) {
            diffTotal.put(r.getDifficulty(), diffTotal.getOrDefault(r.getDifficulty(), 0) + 1);
            if (r.isCorrect()) {
                diffCorrect.put(r.getDifficulty(), diffCorrect.getOrDefault(r.getDifficulty(), 0.0) + 1.0);
                correctInBucket++;
            }
            countInBucket++;
            if (countInBucket == bucketSize) {
                improvementTrend.add((double) correctInBucket / bucketSize);
                correctInBucket = 0;
                countInBucket = 0;
            }
        }
        if (countInBucket > 0) {
            improvementTrend.add((double) correctInBucket / countInBucket); // final partial bucket
        }
        // keep only last 10 trends for recent improvement
        if (improvementTrend.size() > 10) {
            improvementTrend = improvementTrend.subList(improvementTrend.size() - 10, improvementTrend.size());
        }

        Map<Integer, Double> difficultyPerformance = new HashMap<>();
        for (Integer diff : diffTotal.keySet()) {
            difficultyPerformance.put(diff, diffCorrect.getOrDefault(diff, 0.0) / diffTotal.get(diff));
        }

        // Generate AI Study Insights
        String aiInsight = getAiStudyInsight(userId, (int) totalQuestions, accuracy, currentStreak, weaknesses);

        // Compile result
        Map<String, Object> analytics = new HashMap<>();
        analytics.put("totalAttempts", totalQuestions);
        analytics.put("correctAttempts", correctAnswers);
        analytics.put("accuracy", accuracy);
        analytics.put("streak", currentStreak);
        analytics.put("strengths", strengths);
        analytics.put("weaknesses", weaknesses);
        analytics.put("aiInsight", aiInsight);
        
        // New Quiz Analytics
        analytics.put("topicPerformance", topicPerformance);
        analytics.put("difficultyPerformance", difficultyPerformance);
        analytics.put("improvementTrend", improvementTrend);

        return analytics;
    }

    public StudentDashboardResponse getStudentDashboard(String userId) {
        StudentDashboardResponse response = new StudentDashboardResponse();
        
        StudentLearningProfile profile = profileRepo.findByUserId(userId).orElse(new StudentLearningProfile());
        response.setCurrentLevel(profile.getCurrentLevel() != null ? profile.getCurrentLevel().name() : StudentLevel.BEGINNER.name());
        response.setMasteryScore(profile.getOverallMastery());
        response.setAverageScore(profile.getAverageScore());
        response.setStudyStreak(profile.getStreakDays());
        
        List<TopicMastery> masteries = masteryRepo.findByStudentId(userId);
        
        List<String> weakTopics = masteries.stream()
                .filter(m -> m.getMasteryScore() <= 40.0)
                .map(TopicMastery::getTopic)
                .collect(Collectors.toList());
        response.setWeakTopics(weakTopics);
        
        List<String> strongTopics = masteries.stream()
                .filter(m -> m.getMasteryScore() >= 71.0)
                .map(TopicMastery::getTopic)
                .collect(Collectors.toList());
        response.setStrongTopics(strongTopics);
        
        response.setRecentProgress("You have maintained a " + profile.getStreakDays() + " day streak!");
        return response;
    }

    public FacultyDashboardResponse getFacultyDashboard() {
        FacultyDashboardResponse response = new FacultyDashboardResponse();

        // 1. Class Overview
        long totalStudents = mongoTemplate.count(new org.springframework.data.mongodb.core.query.Query(), StudentLearningProfile.class);
        response.setTotalStudents((int) totalStudents);

        org.springframework.data.mongodb.core.aggregation.Aggregation avgMasteryAgg = org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation(
                org.springframework.data.mongodb.core.aggregation.Aggregation.group().avg("overallMastery").as("averageMastery")
        );
        org.springframework.data.mongodb.core.aggregation.AggregationResults<org.bson.Document> avgMasteryResult = 
            mongoTemplate.aggregate(avgMasteryAgg, StudentLearningProfile.class, org.bson.Document.class);
        double avgMastery = avgMasteryResult.getUniqueMappedResult() != null && avgMasteryResult.getUniqueMappedResult().get("averageMastery") != null ? 
            ((Number) avgMasteryResult.getUniqueMappedResult().get("averageMastery")).doubleValue() : 0.0;
        response.setAverageMastery(avgMastery);

        // activeStudents: count distinct userIds in ResponseRecord over last 7 days
        java.time.Instant sevenDaysAgo = java.time.Instant.now().minus(java.time.Duration.ofDays(7));
        org.springframework.data.mongodb.core.query.Criteria recentCriteria = org.springframework.data.mongodb.core.query.Criteria.where("timestamp").gte(sevenDaysAgo.toString());
        List<String> activeUsers = mongoTemplate.findDistinct(
                new org.springframework.data.mongodb.core.query.Query(recentCriteria), 
                "userId", ResponseRecord.class, String.class);
        response.setActiveStudents(activeUsers.size());

        // 2. Topic Analysis
        org.springframework.data.mongodb.core.aggregation.Aggregation topicAggDesc = org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation(
                org.springframework.data.mongodb.core.aggregation.Aggregation.group("topic").avg("masteryScore").as("avgMastery"),
                org.springframework.data.mongodb.core.aggregation.Aggregation.sort(org.springframework.data.domain.Sort.Direction.DESC, "avgMastery"),
                org.springframework.data.mongodb.core.aggregation.Aggregation.limit(5)
        );
        org.springframework.data.mongodb.core.aggregation.AggregationResults<org.bson.Document> topicDescResult = 
            mongoTemplate.aggregate(topicAggDesc, TopicMastery.class, org.bson.Document.class);
        response.setStrongestTopics(topicDescResult.getMappedResults().stream().map(d -> d.getString("_id")).collect(Collectors.toList()));

        org.springframework.data.mongodb.core.aggregation.Aggregation topicAggAsc = org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation(
                org.springframework.data.mongodb.core.aggregation.Aggregation.group("topic").avg("masteryScore").as("avgMastery"),
                org.springframework.data.mongodb.core.aggregation.Aggregation.sort(org.springframework.data.domain.Sort.Direction.ASC, "avgMastery"),
                org.springframework.data.mongodb.core.aggregation.Aggregation.limit(5)
        );
        org.springframework.data.mongodb.core.aggregation.AggregationResults<org.bson.Document> topicAscResult = 
            mongoTemplate.aggregate(topicAggAsc, TopicMastery.class, org.bson.Document.class);
        response.setWeakestTopics(topicAscResult.getMappedResults().stream().map(d -> d.getString("_id")).collect(Collectors.toList()));

        // 3. Student Analysis
        org.springframework.data.mongodb.core.aggregation.Aggregation topPerformersAgg = org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation(
                org.springframework.data.mongodb.core.aggregation.Aggregation.sort(org.springframework.data.domain.Sort.Direction.DESC, "overallMastery"),
                org.springframework.data.mongodb.core.aggregation.Aggregation.limit(5)
        );
        response.setTopPerformers(mongoTemplate.aggregate(topPerformersAgg, StudentLearningProfile.class, StudentLearningProfile.class)
                .getMappedResults().stream().map(StudentLearningProfile::getUserId).collect(Collectors.toList()));

        org.springframework.data.mongodb.core.aggregation.Aggregation atRiskAgg = org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation(
                org.springframework.data.mongodb.core.aggregation.Aggregation.sort(org.springframework.data.domain.Sort.Direction.ASC, "overallMastery"),
                org.springframework.data.mongodb.core.aggregation.Aggregation.limit(5)
        );
        response.setAtRiskStudents(mongoTemplate.aggregate(atRiskAgg, StudentLearningProfile.class, StudentLearningProfile.class)
                .getMappedResults().stream().map(StudentLearningProfile::getUserId).collect(Collectors.toList()));

        // Most Improved
        org.springframework.data.mongodb.core.aggregation.Aggregation mostImprovedAgg = org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation(
                org.springframework.data.mongodb.core.aggregation.Aggregation.match(org.springframework.data.mongodb.core.query.Criteria.where("knowledgeGrowthTrend.1").exists(true)),
                org.springframework.data.mongodb.core.aggregation.Aggregation.project("userId")
                    .and(org.springframework.data.mongodb.core.aggregation.ArrayOperators.ArrayElemAt.arrayOf("knowledgeGrowthTrend").elementAt(-1)).as("lastVal")
                    .and(org.springframework.data.mongodb.core.aggregation.ArrayOperators.ArrayElemAt.arrayOf("knowledgeGrowthTrend").elementAt(0)).as("firstVal"),
                org.springframework.data.mongodb.core.aggregation.Aggregation.project("userId")
                    .and(org.springframework.data.mongodb.core.aggregation.ArithmeticOperators.Subtract.valueOf("lastVal").subtract("firstVal")).as("growth"),
                org.springframework.data.mongodb.core.aggregation.Aggregation.sort(org.springframework.data.domain.Sort.Direction.DESC, "growth"),
                org.springframework.data.mongodb.core.aggregation.Aggregation.limit(5)
        );
        response.setMostImprovedStudents(mongoTemplate.aggregate(mostImprovedAgg, StudentLearningProfile.class, org.bson.Document.class)
                .getMappedResults().stream().map(d -> d.getString("userId")).collect(Collectors.toList()));

        // Most active students based on streak
        org.springframework.data.mongodb.core.aggregation.Aggregation mostActiveAgg = org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation(
                org.springframework.data.mongodb.core.aggregation.Aggregation.sort(org.springframework.data.domain.Sort.Direction.DESC, "streakDays"),
                org.springframework.data.mongodb.core.aggregation.Aggregation.limit(5)
        );
        response.setMostActiveStudents(mongoTemplate.aggregate(mostActiveAgg, StudentLearningProfile.class, StudentLearningProfile.class)
                .getMappedResults().stream().map(StudentLearningProfile::getUserId).collect(Collectors.toList()));

        // 4. Engagement Metrics
        Map<String, Object> engagement = new HashMap<>();
        String todayStart = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS).toString();
        long dailyActivity = mongoTemplate.count(new org.springframework.data.mongodb.core.query.Query(org.springframework.data.mongodb.core.query.Criteria.where("timestamp").gte(todayStart)), ResponseRecord.class);
        engagement.put("dailyActivity", dailyActivity);
        
        List<String> quizParticipants = mongoTemplate.findDistinct(new org.springframework.data.mongodb.core.query.Query(), "userId", ResponseRecord.class, String.class);
        engagement.put("quizParticipation", quizParticipants.size());
        
        long discussionThreads = mongoTemplate.count(new org.springframework.data.mongodb.core.query.Query(), "discussion_threads");
        long discussionReplies = mongoTemplate.count(new org.springframework.data.mongodb.core.query.Query(), "discussion_replies");
        engagement.put("discussionParticipation", discussionThreads + discussionReplies);

        response.setEngagementMetrics(engagement);

        return response;
    }

    /**
     * Calculate consecutive days of active study.
     */
    public int calculateDailyStreak(String userId) {
        // Fetch only distinct active days formatted as YYYY-MM-DD
        org.springframework.data.mongodb.core.aggregation.Aggregation agg = org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation(
                org.springframework.data.mongodb.core.aggregation.Aggregation.match(org.springframework.data.mongodb.core.query.Criteria.where("userId").is(userId)),
                org.springframework.data.mongodb.core.aggregation.Aggregation.project()
                        .andExpression("{$dateToString: {format: '%Y-%m-%d', date: {$toDate: '$timestamp'}, timezone: 'UTC'}}").as("date"),
                org.springframework.data.mongodb.core.aggregation.Aggregation.group("date")
        );

        org.springframework.data.mongodb.core.aggregation.AggregationResults<org.bson.Document> results = mongoTemplate.aggregate(agg, "responses", org.bson.Document.class);
        Set<String> activeDays = results.getMappedResults().stream()
                .map(doc -> doc.getString("_id"))
                .collect(Collectors.toSet());

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        int streak = 0;

        LocalDate checkDate = today;
        if (!activeDays.contains(checkDate.toString())) {
            // Check if active yesterday, then count streak from there
            checkDate = today.minusDays(1);
        }

        while (activeDays.contains(checkDate.toString())) {
            streak++;
            checkDate = checkDate.minusDays(1);
        }

        return streak;
    }

    /**
     * Queries Mistral Mistral to get personal tutoring dashboard advice.
     */
    private String getAiStudyInsight(String userId, int total, double accuracy, int streak, List<Map<String, Object>> weaknesses) {
        if (total == 0) {
            return "No quiz data yet. Start your first quiz to unlock AI-powered study insights!";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("System: You are an encouraging personal tutor advisor. Write a short, highly-specific study guidance (exactly 2-3 sentences) based on the user's progress:\n");
        sb.append(String.format("- Total Questions Answered: %d\n", total));
        sb.append(String.format("- Quiz Accuracy: %.1f%%\n", accuracy * 100));
        sb.append(String.format("- Daily Study Streak: %d days\n", streak));

        if (!weaknesses.isEmpty()) {
            sb.append("- Struggling topics: ");
            String topicsStr = weaknesses.stream().map(w -> (String) w.get("topic")).collect(Collectors.joining(", "));
            sb.append(topicsStr).append("\n");
        } else {
            sb.append("- Struggling topics: None! The student is performing consistently.\n");
        }

        sb.append("Tutor Advice: ");

        try {
            return aiOrchestratorService.generate(sb.toString(), Map.of());
        } catch (Exception e) {
            LOG.warn("Failed to generate AI study insights: {}", e.getMessage());
        }

        return "Keep going! You're making steady progress. Focus on reviewing your weak concepts to build solid mastery.";
    }
}
