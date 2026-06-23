package com.lms.model;

import java.util.Map;

/**
 * Represents the dynamic learning profile of a user.
 *
 * Built in real-time by UserProfileService from the answer history.
 * Consumed by AdaptiveEngineService for scoring and difficulty adjustment.
 *
 * No persistence layer — recomputed per-request for freshness.
 * (Persist via MongoDB if session-level caching is desired later.)
 */
public class UserLearningProfile {

    private String userId;

    // 🧠 Learning behaviour
    private double learningSpeed;     // correct / total (0–1)
    private double retentionRate;     // rolling average correctness

    // 🎯 Preferences
    private int preferredDifficulty;  // avg difficulty of attempted questions

    // 📊 Concept strengths / weaknesses
    private Map<String, Double> conceptStrength;  // +1 correct, −1 wrong per concept

    // ⚠️ Fatigue tracking
    private int    recentAttempts;
    private double fatigueScore;      // 0.7 if > 10 attempts, else 0.2

    // ── Getters / Setters ─────────────────────────────────────────────────

    public String getUserId()              { return userId; }
    public void   setUserId(String userId) { this.userId = userId; }

    public double getLearningSpeed()                  { return learningSpeed; }
    public void   setLearningSpeed(double v)          { this.learningSpeed = v; }

    public double getRetentionRate()                  { return retentionRate; }
    public void   setRetentionRate(double v)          { this.retentionRate = v; }

    public int  getPreferredDifficulty()              { return preferredDifficulty; }
    public void setPreferredDifficulty(int v)         { this.preferredDifficulty = v; }

    public Map<String, Double> getConceptStrength()              { return conceptStrength; }
    public void setConceptStrength(Map<String, Double> v)        { this.conceptStrength = v; }

    public int  getRecentAttempts()                   { return recentAttempts; }
    public void setRecentAttempts(int v)              { this.recentAttempts = v; }

    public double getFatigueScore()                   { return fatigueScore; }
    public void   setFatigueScore(double v)           { this.fatigueScore = v; }
}
