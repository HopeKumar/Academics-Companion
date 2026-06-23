package com.lms.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(RateLimitingFilter.class);

    // Cache of bucket4j buckets per client IP address, expires after 1 hour of inactivity
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .maximumSize(5000)
            .build();

    private Bucket createNewBucket() {
        // Limit: 10 requests per minute per IP for expensive AI endpoints
        return Bucket.builder()
                .addLimit(Bandwidth.classic(10, Refill.intervally(10, Duration.ofMinutes(1))))
                .build();
    }

    private boolean isRateLimitedRoute(String path) {
        return path.equals("/chat") || path.equals("/api/v1/chat")
                || path.equals("/chat/stream") || path.equals("/api/v1/chat/stream")
                || path.equals("/summary/generate") || path.equals("/api/v1/summary/generate") || path.equals("/api/v1/summaries/generate")
                || path.equals("/quiz/generate-adaptive") || path.equals("/api/v1/quiz/generate-adaptive")
                || path.equals("/podcasts/generate") || path.equals("/api/v1/podcasts/generate")
                || path.equals("/mindmaps/generate") || path.equals("/api/v1/mindmaps/generate");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Apply rate limit only to target expensive AI generation endpoints
        if (isRateLimitedRoute(path)) {
            String clientIp = request.getRemoteAddr();
            Bucket bucket = buckets.get(clientIp, k -> createNewBucket());

            if (!bucket.tryConsume(1)) {
                LOG.warn("RATE LIMIT EXCEEDED for IP={} on path={}", clientIp, path);
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Too many requests. Rate limit exceeded for AI generation. Please try again shortly.\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
