package com.lms.model;

import java.util.List;
import java.util.Map;

public class FacultyDashboardResponse {
    private int totalStudents;
    private int activeStudents;
    private double averageMastery;
    
    private List<String> strongestTopics;
    private List<String> weakestTopics;
    
    private List<String> mostActiveStudents;
    private List<String> topPerformers;
    private List<String> atRiskStudents;
    private List<String> mostImprovedStudents;
    
    private Map<String, Object> engagementMetrics;

    // Getters and Setters

    public int getTotalStudents() { return totalStudents; }
    public void setTotalStudents(int totalStudents) { this.totalStudents = totalStudents; }

    public double getAverageMastery() { return averageMastery; }
    public void setAverageMastery(double averageMastery) { this.averageMastery = averageMastery; }

    public List<String> getWeakestTopics() { return weakestTopics; }
    public void setWeakestTopics(List<String> weakestTopics) { this.weakestTopics = weakestTopics; }

    public List<String> getMostActiveStudents() { return mostActiveStudents; }
    public void setMostActiveStudents(List<String> mostActiveStudents) { this.mostActiveStudents = mostActiveStudents; }

    public List<String> getTopPerformers() { return topPerformers; }
    public void setTopPerformers(List<String> topPerformers) { this.topPerformers = topPerformers; }

    public int getActiveStudents() { return activeStudents; }
    public void setActiveStudents(int activeStudents) { this.activeStudents = activeStudents; }

    public List<String> getStrongestTopics() { return strongestTopics; }
    public void setStrongestTopics(List<String> strongestTopics) { this.strongestTopics = strongestTopics; }

    public List<String> getAtRiskStudents() { return atRiskStudents; }
    public void setAtRiskStudents(List<String> atRiskStudents) { this.atRiskStudents = atRiskStudents; }

    public List<String> getMostImprovedStudents() { return mostImprovedStudents; }
    public void setMostImprovedStudents(List<String> mostImprovedStudents) { this.mostImprovedStudents = mostImprovedStudents; }

    public Map<String, Object> getEngagementMetrics() { return engagementMetrics; }
    public void setEngagementMetrics(Map<String, Object> engagementMetrics) { this.engagementMetrics = engagementMetrics; }
}
