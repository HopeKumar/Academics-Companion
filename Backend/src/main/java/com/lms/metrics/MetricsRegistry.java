package com.lms.metrics;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Application metrics registry.
 *
 * Converted from Vert.x version:
 *   - Removed io.vertx.core.json.JsonObject — replaced with Map<String,Object>
 *   - @Component added for Spring singleton management
 *   - Singleton via Spring context (was a static INSTANCE before)
 *   - All metric logic PRESERVED EXACTLY:
 *       - Rolling averages (ROLLING_WINDOW = 20)
 *       - Slow request detection
 *       - AI call / timeout / error tracking
 *       - summarySnapshot() for health status + feedback loop
 *       - snapshot() for full detail
 */
@Component
public class MetricsRegistry {

    private static final int ROLLING_WINDOW = 20;

    // ── Per-endpoint stats ────────────────────────────────────────────────

    private final ConcurrentHashMap<String, EndpointStats> endpoints = new ConcurrentHashMap<>();

    // ── AI metrics ────────────────────────────────────────────────────────

    private final AtomicLong aiCalls     = new AtomicLong();
    private final AtomicLong aiCacheHits = new AtomicLong();
    private final AtomicLong aiTotalMs   = new AtomicLong();
    private final AtomicLong aiTimeouts  = new AtomicLong();
    private final AtomicLong aiErrors    = new AtomicLong();

    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;

    @Autowired
    public MetricsRegistry(io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /** Record a request with elapsed time and error flag. */
    public void recordRequest(String endpoint, long elapsedMs, boolean error) {
        EndpointStats s = endpoints.computeIfAbsent(endpoint, k -> new EndpointStats());
        s.requests.incrementAndGet();
        s.totalMs.addAndGet(elapsedMs);
        if (error) s.errors.incrementAndGet();
        s.maxMs.updateAndGet(current -> Math.max(current, elapsedMs));
        s.pushRolling(elapsedMs);

        meterRegistry.timer("app.requests", "endpoint", endpoint).record(java.time.Duration.ofMillis(elapsedMs));
        if (error) {
            meterRegistry.counter("app.requests.errors", "endpoint", endpoint).increment();
        }
    }

    /** Record a slow request (>2s). */
    public void recordSlowRequest(String endpoint, long elapsedMs) {
        EndpointStats s = endpoints.computeIfAbsent(endpoint, k -> new EndpointStats());
        s.slowRequests.incrementAndGet();
        globalSlowRequests.incrementAndGet();
        meterRegistry.counter("app.requests.slow", "endpoint", endpoint).increment();
    }

    public void recordAiCall(long elapsedMs, boolean cacheHit) {
        aiCalls.incrementAndGet();
        if (cacheHit) {
            aiCacheHits.incrementAndGet();
            meterRegistry.counter("app.ai.cache_hits").increment();
        } else {
            aiTotalMs.addAndGet(elapsedMs);
            meterRegistry.timer("app.ai.calls").record(java.time.Duration.ofMillis(elapsedMs));
        }
    }

    public void recordAiTimeout() { 
        aiTimeouts.incrementAndGet(); 
        meterRegistry.counter("app.ai.timeouts").increment();
    }
    public void recordAiError()   { 
        aiErrors.incrementAndGet(); 
        meterRegistry.counter("app.ai.errors").increment();
    }

    public void recordUpload(long elapsedMs) {
        meterRegistry.timer("app.upload.latency").record(java.time.Duration.ofMillis(elapsedMs));
    }

    public void recordStorage(String operation, long elapsedMs) {
        meterRegistry.timer("app.storage.latency", "operation", operation).record(java.time.Duration.ofMillis(elapsedMs));
    }

    public void recordGenerationSuccess(String feature) {
        meterRegistry.counter("app.generation.success", "feature", feature).increment();
    }

    public void recordGenerationFailure(String feature) {
        meterRegistry.counter("app.generation.failure", "feature", feature).increment();
    }

    public void recordAuth(String action, String status) {
        meterRegistry.counter("app.auth", "action", action, "status", status).increment();
    }

    // ── Global slow counter ───────────────────────────────────────────────

    private final AtomicLong globalSlowRequests = new AtomicLong();

    // ── Full detail snapshot (GET /metrics) ───────────────────────────────

    public Map<String, Object> snapshot() {
        Map<String, Object> endpointMap = new HashMap<>();
        long allRequests = 0;
        long allErrors   = 0;

        for (var entry : endpoints.entrySet()) {
            EndpointStats s   = entry.getValue();
            long          req = s.requests.get();
            long          ms  = s.totalMs.get();
            long          err = s.errors.get();
            allRequests += req;
            allErrors   += err;

            double errorRate   = req == 0 ? 0.0 : Math.round(err * 1000.0 / req) / 1000.0;
            long   rollingAvg  = s.rollingAvgMs();

            Map<String, Object> epData = new HashMap<>();
            epData.put("requests",     req);
            epData.put("errors",       err);
            epData.put("avgMs",        req == 0 ? 0 : ms / req);
            epData.put("rollingAvgMs", rollingAvg);
            epData.put("maxMs",        s.maxMs.get());
            epData.put("slowRequests", s.slowRequests.get());
            epData.put("errorRate",    errorRate);
            endpointMap.put(entry.getKey(), epData);
        }

        long   calls       = aiCalls.get();
        long   net         = calls - aiCacheHits.get();
        long   netMs       = aiTotalMs.get();
        double cacheHitRate = calls == 0 ? 0.0
                : Math.round(aiCacheHits.get() * 1000.0 / calls) / 1000.0;

        Map<String, Object> aiData = new HashMap<>();
        aiData.put("totalCalls",   calls);
        aiData.put("cacheHits",    aiCacheHits.get());
        aiData.put("cacheHitRate", cacheHitRate);
        aiData.put("timeouts",     aiTimeouts.get());
        aiData.put("errors",       aiErrors.get());
        aiData.put("avgNetworkMs", net == 0 ? 0 : netMs / net);

        Map<String, Object> result = new HashMap<>();
        result.put("endpoints",         endpointMap);
        result.put("ai",                aiData);
        result.put("totalSlowRequests", globalSlowRequests.get());
        return result;
    }

    /**
     * Concise health snapshot — used by feedback loop in TestService
     * and exposed at GET /metrics/summary.
     */
    public Map<String, Object> summarySnapshot() {
        long allRequests = 0;
        long allErrors   = 0;

        for (EndpointStats s : endpoints.values()) {
            allRequests += s.requests.get();
            allErrors   += s.errors.get();
        }

        double globalErrorRate = allRequests == 0 ? 0.0
                : Math.round(allErrors * 10000.0 / allRequests) / 10000.0;

        long   calls        = aiCalls.get();
        long   net          = calls - aiCacheHits.get();
        double cacheHitRate = calls == 0 ? 0.0
                : Math.round(aiCacheHits.get() * 1000.0 / calls) / 1000.0;

        String status;
        if      (globalErrorRate > 0.20)              status = "critical";
        else if (globalErrorRate > 0.05 || aiTimeouts.get() > 0) status = "degraded";
        else                                           status = "healthy";

        Map<String, Object> summary = new HashMap<>();
        summary.put("status",            status);
        summary.put("globalErrorRate",   globalErrorRate);
        summary.put("totalRequests",     allRequests);
        summary.put("totalErrors",       allErrors);
        summary.put("totalSlowRequests", globalSlowRequests.get());
        summary.put("aiCacheHitRate",    cacheHitRate);
        summary.put("aiAvgNetworkMs",    net == 0 ? 0 : aiTotalMs.get() / net);
        summary.put("aiTimeouts",        aiTimeouts.get());
        summary.put("aiErrors",          aiErrors.get());
        return summary;
    }

    // ── Inner stat holder ─────────────────────────────────────────────────

    private static class EndpointStats {
        final AtomicLong requests     = new AtomicLong();
        final AtomicLong errors       = new AtomicLong();
        final AtomicLong totalMs      = new AtomicLong();
        final AtomicLong maxMs        = new AtomicLong();
        final AtomicLong slowRequests = new AtomicLong();

        private final Deque<Long>      rollingWindow = new ArrayDeque<>(ROLLING_WINDOW + 1);
        private final ReentrantLock    rollingLock   = new ReentrantLock();

        void pushRolling(long ms) {
            rollingLock.lock();
            try {
                rollingWindow.addLast(ms);
                if (rollingWindow.size() > ROLLING_WINDOW) rollingWindow.pollFirst();
            } finally {
                rollingLock.unlock();
            }
        }

        long rollingAvgMs() {
            rollingLock.lock();
            try {
                if (rollingWindow.isEmpty()) return 0;
                long sum = 0;
                for (long v : rollingWindow) sum += v;
                return sum / rollingWindow.size();
            } finally {
                rollingLock.unlock();
            }
        }
    }
}
