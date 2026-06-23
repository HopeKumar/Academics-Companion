package com.lms.controller;

import com.lms.config.AppConfig;
import com.lms.metrics.MetricsRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller exposing application metrics.
 *
 * Replaces the Vert.x MetricsController (Router + RoutingContext).
 *
 * Endpoints:
 *   GET /metrics         → full detail snapshot
 *   GET /metrics/summary → concise health status (used by feedback loop)
 *
 * Both endpoints are conditionally enabled by AppConfig.METRICS_ENABLED.
 */
@RestController
@RequestMapping({"/metrics", "/api/v1/metrics"})
public class MetricsController {

    private static final Logger LOG = LoggerFactory.getLogger(MetricsController.class);

    private final MetricsRegistry metrics;

    @Autowired
    public MetricsController(MetricsRegistry metrics) {
        this.metrics = metrics;
        if (AppConfig.METRICS_ENABLED) {
            LOG.info("Metrics endpoints registered at GET /metrics and GET /metrics/summary");
        } else {
            LOG.info("Metrics endpoints disabled (AppConfig.METRICS_ENABLED=false)");
        }
    }

    // ── GET /metrics (full detail) ────────────────────────────────────────

    @GetMapping
    public ResponseEntity<?> getMetrics() {
        if (!AppConfig.METRICS_ENABLED) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(metrics.snapshot());
    }

    // ── GET /metrics/summary (actionable health status) ───────────────────

    @GetMapping("/summary")
    public ResponseEntity<?> getMetricsSummary() {
        if (!AppConfig.METRICS_ENABLED) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(metrics.summarySnapshot());
    }
}
