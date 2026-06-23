package com.lms.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.Map;

@Document(collection = "study_plans")
public class StudyPlan {

    @Id
    private String id;

    @Indexed(unique = true)
    private String userId;

    private String generatedAt;
    private String availableTime;

    // Use structured data: List of Days with Tasks
    private List<Map<String, Object>> dailyPlan;
    private List<Map<String, Object>> weeklyPlan;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(String generatedAt) { this.generatedAt = generatedAt; }

    public String getAvailableTime() { return availableTime; }
    public void setAvailableTime(String availableTime) { this.availableTime = availableTime; }

    public List<Map<String, Object>> getDailyPlan() { return dailyPlan; }
    public void setDailyPlan(List<Map<String, Object>> dailyPlan) { this.dailyPlan = dailyPlan; }

    public List<Map<String, Object>> getWeeklyPlan() { return weeklyPlan; }
    public void setWeeklyPlan(List<Map<String, Object>> weeklyPlan) { this.weeklyPlan = weeklyPlan; }
}
