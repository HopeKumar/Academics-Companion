package com.lms.events;

import org.springframework.context.ApplicationEvent;

public class SummaryGeneratedEvent extends ApplicationEvent {
    private final String sourceId;
    private final String userId;
    private final String status;

    public SummaryGeneratedEvent(Object source, String sourceId, String userId, String status) {
        super(source);
        this.sourceId = sourceId;
        this.userId = userId;
        this.status = status;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getUserId() {
        return userId;
    }

    public String getStatus() {
        return status;
    }
}
