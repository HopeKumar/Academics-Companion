package com.lms.service;

import com.lms.model.JobStatus;
import com.lms.repository.JobStatusRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class JobStatusService {

    private static final Logger LOG = LoggerFactory.getLogger(JobStatusService.class);

    private final JobStatusRepository repository;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    public JobStatusService(JobStatusRepository repository, org.springframework.context.ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    public JobStatus createOrUpdateJob(String sourceId, String type, String status, String message) {
        Optional<JobStatus> existing = repository.findBySourceIdAndType(sourceId, type);
        JobStatus job = existing.orElseGet(() -> new JobStatus(sourceId, type, status));
        
        job.setStatus(status);
        if (message != null) {
            job.setMessage(message);
        }
        
        LOG.info("JobStatus Update: sourceId={}, type={}, status={}", sourceId, type, status);
        JobStatus saved = repository.save(job);
        eventPublisher.publishEvent(new com.lms.events.JobStatusEvent(this, saved));
        return saved;
    }

    public List<JobStatus> getJobsForSource(String sourceId) {
        return repository.findBySourceId(sourceId);
    }

    public Optional<JobStatus> getJobStatus(String sourceId, String type) {
        return repository.findBySourceIdAndType(sourceId, type);
    }
}
