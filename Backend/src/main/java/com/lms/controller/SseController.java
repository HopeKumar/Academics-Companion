package com.lms.controller;

import com.lms.events.JobStatusEvent;
import com.lms.model.JobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping({"/events", "/api/v1/events"})
public class SseController {

    private static final Logger LOG = LoggerFactory.getLogger(SseController.class);

    // Map from sourceId to a list of active SseEmitters
    private final ConcurrentHashMap<String, List<SseEmitter>> emittersMap = new ConcurrentHashMap<>();

    @GetMapping(value = "/subscribe/{sourceId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@PathVariable("sourceId") String sourceId) {
        LOG.info("SSE client subscribing for sourceId: {}", sourceId);
        
        // Use a 15-minute timeout (900_000 milliseconds)
        SseEmitter emitter = new SseEmitter(900_000L);
        
        emittersMap.compute(sourceId, (key, list) -> {
            if (list == null) {
                list = new ArrayList<>();
            }
            list.add(emitter);
            return list;
        });

        emitter.onCompletion(() -> removeEmitter(sourceId, emitter));
        emitter.onTimeout(() -> removeEmitter(sourceId, emitter));
        emitter.onError((ex) -> removeEmitter(sourceId, emitter));

        // Send a connection confirmation event
        try {
            emitter.send(SseEmitter.event()
                    .name("CONNECTED")
                    .data(Map.of("message", "Subscribed successfully to source " + sourceId)));
        } catch (IOException e) {
            LOG.warn("Failed to send initial confirmation SSE to client for sourceId: {}", sourceId);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    private void removeEmitter(String sourceId, SseEmitter emitter) {
        emittersMap.computeIfPresent(sourceId, (key, list) -> {
            list.remove(emitter);
            if (list.isEmpty()) {
                return null;
            }
            return list;
        });
    }

    @EventListener
    public void handleJobStatusEvent(JobStatusEvent event) {
        JobStatus jobStatus = event.getJobStatus();
        String sourceId = jobStatus.getSourceId();
        
        List<SseEmitter> emitters = emittersMap.get(sourceId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        LOG.info("SSE: Broadcasting job update to subscribers for sourceId={}: {} -> {}", sourceId, jobStatus.getType(), jobStatus.getStatus());
        
        List<SseEmitter> deadEmitters = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("JOB_STATUS")
                        .data(jobStatus));
            } catch (Exception e) {
                deadEmitters.add(emitter);
            }
        }

        if (!deadEmitters.isEmpty()) {
            emittersMap.computeIfPresent(sourceId, (key, list) -> {
                list.removeAll(deadEmitters);
                if (list.isEmpty()) {
                    return null;
                }
                return list;
            });
        }
    }
}
