package com.lms.events;

import org.springframework.context.ApplicationEvent;

public class DocumentUploadedEvent extends ApplicationEvent {
    private final String sourceId;
    private final String userId;

    public DocumentUploadedEvent(Object source, String sourceId, String userId) {
        super(source);
        this.sourceId = sourceId;
        this.userId = userId;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getUserId() {
        return userId;
    }
}
