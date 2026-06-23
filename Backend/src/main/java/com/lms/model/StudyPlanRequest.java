package com.lms.model;

import jakarta.validation.constraints.NotBlank;

public class StudyPlanRequest {
    @NotBlank(message = "Available time is required")
    private String availableTime;

    public String getAvailableTime() {
        return availableTime;
    }

    public void setAvailableTime(String availableTime) {
        this.availableTime = availableTime;
    }
}
