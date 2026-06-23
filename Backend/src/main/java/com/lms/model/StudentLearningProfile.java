package com.lms.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Document(collection = "student_learning_profiles")
public class StudentLearningProfile {

    @Id
    private String id;
    
    @Indexed(unique = true)
    private String userId;
    
    private double overallMastery;
    
    private StudentLevel currentLevel;
    
    private int totalQuizzes;
    
    private double averageScore;
    
    private int streakDays;
    
    private String lastActivity;
    
    private String createdAt;
    
    private String updatedAt;

    // New Mastery Model Fields
    private double topicConsistency;
    private double revisionFrequency;
    private double engagementScore;

    private List<Double> knowledgeGrowthTrend = new ArrayList<>();
    private Map<String, String> subjectMasteryTrend = new HashMap<>();
    private Map<String, String> topicMasteryTrend = new HashMap<>();

    public StudentLearningProfile() {
        this.createdAt = Instant.now().toString();
        this.updatedAt = Instant.now().toString();
        this.currentLevel = StudentLevel.BEGINNER;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public double getOverallMastery() {
        return overallMastery;
    }

    public void setOverallMastery(double overallMastery) {
        this.overallMastery = overallMastery;
    }

    public StudentLevel getCurrentLevel() {
        return currentLevel;
    }

    public void setCurrentLevel(StudentLevel currentLevel) {
        this.currentLevel = currentLevel;
    }

    public int getTotalQuizzes() {
        return totalQuizzes;
    }

    public void setTotalQuizzes(int totalQuizzes) {
        this.totalQuizzes = totalQuizzes;
    }

    public double getAverageScore() {
        return averageScore;
    }

    public void setAverageScore(double averageScore) {
        this.averageScore = averageScore;
    }

    public int getStreakDays() {
        return streakDays;
    }

    public void setStreakDays(int streakDays) {
        this.streakDays = streakDays;
    }

    public String getLastActivity() {
        return lastActivity;
    }

    public void setLastActivity(String lastActivity) {
        this.lastActivity = lastActivity;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public double getTopicConsistency() {
        return topicConsistency;
    }

    public void setTopicConsistency(double topicConsistency) {
        this.topicConsistency = topicConsistency;
    }

    public double getRevisionFrequency() {
        return revisionFrequency;
    }

    public void setRevisionFrequency(double revisionFrequency) {
        this.revisionFrequency = revisionFrequency;
    }

    public double getEngagementScore() {
        return engagementScore;
    }

    public void setEngagementScore(double engagementScore) {
        this.engagementScore = engagementScore;
    }

    public List<Double> getKnowledgeGrowthTrend() {
        return knowledgeGrowthTrend;
    }

    public void setKnowledgeGrowthTrend(List<Double> knowledgeGrowthTrend) {
        this.knowledgeGrowthTrend = knowledgeGrowthTrend;
    }

    public Map<String, String> getSubjectMasteryTrend() {
        return subjectMasteryTrend;
    }

    public void setSubjectMasteryTrend(Map<String, String> subjectMasteryTrend) {
        this.subjectMasteryTrend = subjectMasteryTrend;
    }

    public Map<String, String> getTopicMasteryTrend() {
        return topicMasteryTrend;
    }

    public void setTopicMasteryTrend(Map<String, String> topicMasteryTrend) {
        this.topicMasteryTrend = topicMasteryTrend;
    }
}
