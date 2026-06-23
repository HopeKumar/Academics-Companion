package com.lms.events;

import com.lms.model.JobStatus;
import org.springframework.context.ApplicationEvent;

public class JobStatusEvent extends ApplicationEvent {
    private final JobStatus jobStatus;

    public JobStatusEvent(Object source, JobStatus jobStatus) {
        super(source);
        this.jobStatus = jobStatus;
    }

    public JobStatus getJobStatus() {
        return jobStatus;
    }
}
