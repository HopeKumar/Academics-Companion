package com.lms.config;

import com.lms.service.TokenBlacklistService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Custom Micrometer metrics registered at startup.
 *
 * Supplements the auto-configured JVM, thread, and HTTP metrics with
 * ABF-specific gauges:
 *   - abf.security.blacklisted_tokens   → size of the token blacklist
 *
 * Additional custom metrics are recorded directly in MetricsRegistry.java.
 */
@Configuration
public class MonitoringConfig {

    private final MeterRegistry        meterRegistry;
    private final TokenBlacklistService blacklistService;

    public MonitoringConfig(MeterRegistry meterRegistry,
                            TokenBlacklistService blacklistService) {
        this.meterRegistry    = meterRegistry;
        this.blacklistService = blacklistService;
    }

    @PostConstruct
    public void registerCustomGauges() {
        // Track token blacklist size (security posture indicator)
        Gauge.builder("abf.security.blacklisted_tokens",
                        blacklistService, TokenBlacklistService::size)
                .description("Number of currently blacklisted JWT tokens")
                .register(meterRegistry);
    }
}
