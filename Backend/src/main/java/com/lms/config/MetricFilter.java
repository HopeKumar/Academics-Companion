package com.lms.config;

import com.lms.metrics.MetricsRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class MetricFilter extends OncePerRequestFilter {

    private final MetricsRegistry metricsRegistry;

    public MetricFilter(MetricsRegistry metricsRegistry) {
        this.metricsRegistry = metricsRegistry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Skip static resources or actuator endpoints to keep metrics clean
        if (path.startsWith("/actuator") || path.endsWith(".html") || path.endsWith(".js") || path.endsWith(".css") || path.endsWith(".ico") || path.equals("/")) {
            filterChain.doFilter(request, response);
            return;
        }

        long startTime = System.currentTimeMillis();
        boolean hasError = false;

        try {
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            hasError = true;
            throw e;
        } finally {
            long elapsed = System.currentTimeMillis() - startTime;
            int status = response.getStatus();
            if (status >= 400) {
                hasError = true;
            }
            
            // Standardize path to avoid high cardinality in prometheus (e.g. replace IDs)
            String normalizedPath = path.replaceAll("/[0-9a-fA-F-]{24,}", "/{id}")
                                        .replaceAll("/\\d+", "/{id}");

            metricsRegistry.recordRequest(normalizedPath, elapsed, hasError);

            if (elapsed > 2000) {
                metricsRegistry.recordSlowRequest(normalizedPath, elapsed);
            }
        }
    }
}
