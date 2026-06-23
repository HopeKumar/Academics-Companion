package com.lms.model;

import java.util.List;

public class StudentDashboardResponse {
    private String currentLevel;
    private double masteryScore;
    private double averageScore;
    private List<String> weakTopics;
    private List<String> strongTopics;
    private String recentProgress;
    private int studyStreak;

    // Getters and Setters

    public String getCurrentLevel() { return currentLevel; }
    public void setCurrentLevel(String currentLevel) { this.currentLevel = currentLevel; }

    public double getMasteryScore() { return masteryScore; }
    public void setMasteryScore(double masteryScore) { this.masteryScore = masteryScore; }

    public double getAverageScore() { return averageScore; }
    public void setAverageScore(double averageScore) { this.averageScore = averageScore; }

    public List<String> getWeakTopics() { return weakTopics; }
    public void setWeakTopics(List<String> weakTopics) { this.weakTopics = weakTopics; }

    public List<String> getStrongTopics() { return strongTopics; }
    public void setStrongTopics(List<String> strongTopics) { this.strongTopics = strongTopics; }

    public String getRecentProgress() { return recentProgress; }
    public void setRecentProgress(String recentProgress) { this.recentProgress = recentProgress; }

    public int getStudyStreak() { return studyStreak; }
    public void setStudyStreak(int studyStreak) { this.studyStreak = studyStreak; }
}
