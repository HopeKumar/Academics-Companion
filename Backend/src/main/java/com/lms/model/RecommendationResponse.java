package com.lms.model;

import java.util.List;
import java.util.Map;

public class RecommendationResponse {

    private List<String> recommendedFlashcards;
    private List<String> recommendedQuizzes;
    private List<String> recommendedSummaries;
    private List<String> recommendedMindMaps;
    private List<String> recommendedStudySessions;
    private List<String> weakTopics;
    private Map<String, Object> systemInsight;

    public RecommendationResponse() {
    }

    public List<String> getRecommendedFlashcards() { return recommendedFlashcards; }
    public void setRecommendedFlashcards(List<String> v) { this.recommendedFlashcards = v; }

    public List<String> getRecommendedQuizzes() { return recommendedQuizzes; }
    public void setRecommendedQuizzes(List<String> v) { this.recommendedQuizzes = v; }

    public List<String> getRecommendedSummaries() { return recommendedSummaries; }
    public void setRecommendedSummaries(List<String> v) { this.recommendedSummaries = v; }

    public List<String> getRecommendedMindMaps() { return recommendedMindMaps; }
    public void setRecommendedMindMaps(List<String> v) { this.recommendedMindMaps = v; }

    public List<String> getRecommendedStudySessions() { return recommendedStudySessions; }
    public void setRecommendedStudySessions(List<String> v) { this.recommendedStudySessions = v; }

    public List<String> getWeakTopics() { return weakTopics; }
    public void setWeakTopics(List<String> v) { this.weakTopics = v; }

    public Map<String, Object> getSystemInsight() { return systemInsight; }
    public void setSystemInsight(Map<String, Object> v) { this.systemInsight = v; }
}
