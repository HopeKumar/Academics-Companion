package com.lms.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class AiMetricsService {

    private final Counter aiRequestsTotal;
    private final Counter aiSuccessTotal;
    private final Counter aiFailureTotal;
    private final Timer aiLatency;

    private final Counter flashcardGenerationSuccess;
    private final Counter slideGenerationSuccess;
    private final Counter mindmapGenerationSuccess;
    private final Counter podcastGenerationSuccess;

    public AiMetricsService(MeterRegistry registry) {
        this.aiRequestsTotal = Counter.builder("ai.requests.total")
                .description("Total number of AI requests sent")
                .register(registry);

        this.aiSuccessTotal = Counter.builder("ai.success.total")
                .description("Total number of successful AI requests")
                .register(registry);

        this.aiFailureTotal = Counter.builder("ai.failure.total")
                .description("Total number of failed AI requests")
                .register(registry);

        this.aiLatency = Timer.builder("ai.latency")
                .description("Latency of AI requests")
                .register(registry);

        this.flashcardGenerationSuccess = Counter.builder("ai.feature.success")
                .tag("feature", "flashcards")
                .description("Successful flashcard generations")
                .register(registry);

        this.slideGenerationSuccess = Counter.builder("ai.feature.success")
                .tag("feature", "slides")
                .description("Successful slide generations")
                .register(registry);

        this.mindmapGenerationSuccess = Counter.builder("ai.feature.success")
                .tag("feature", "mindmaps")
                .description("Successful mindmap generations")
                .register(registry);

        this.podcastGenerationSuccess = Counter.builder("ai.feature.success")
                .tag("feature", "podcasts")
                .description("Successful podcast generations")
                .register(registry);
    }

    public void recordRequest() {
        aiRequestsTotal.increment();
    }

    public void recordSuccess(long latencyMs) {
        aiSuccessTotal.increment();
        aiLatency.record(Duration.ofMillis(latencyMs));
    }

    public void recordFailure() {
        aiFailureTotal.increment();
    }

    public void recordFeatureSuccess(String feature) {
        switch (feature) {
            case "flashcards" -> flashcardGenerationSuccess.increment();
            case "slides" -> slideGenerationSuccess.increment();
            case "mindmaps" -> mindmapGenerationSuccess.increment();
            case "podcasts" -> podcastGenerationSuccess.increment();
        }
    }

    public java.util.Map<String, Object> getMetrics() {
        return java.util.Map.of(
            "totalRequests", aiRequestsTotal.count(),
            "totalSuccess", aiSuccessTotal.count(),
            "totalFailure", aiFailureTotal.count(),
            "averageLatencyMs", aiLatency.mean(java.util.concurrent.TimeUnit.MILLISECONDS),
            "maxLatencyMs", aiLatency.max(java.util.concurrent.TimeUnit.MILLISECONDS)
        );
    }
}
