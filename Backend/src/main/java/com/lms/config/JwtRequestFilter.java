package com.lms.config;

import com.lms.service.AuthService;
import com.lms.service.TokenBlacklistService;
import com.lms.util.JwtTokenUtil;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT authentication filter.
 *
 * Production hardening applied:
 *   - Checks TokenBlacklistService so logged-out tokens are rejected immediately.
 *   - Populates SLF4J MDC with requestId and userId for correlated log tracing.
 *   - Returns 401 JSON (not a Spring redirect) when a blacklisted token is detected.
 *   - MDC is always cleared in a finally block — no context leakage between requests.
 */
@Component
public class JwtRequestFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(JwtRequestFilter.class);

    private final AuthService           authService;
    private final JwtTokenUtil          jwtTokenUtil;
    private final TokenBlacklistService blacklistService;

    @Autowired
    public JwtRequestFilter(@Lazy AuthService authService,
                            JwtTokenUtil jwtTokenUtil,
                            TokenBlacklistService blacklistService) {
        this.authService      = authService;
        this.jwtTokenUtil     = jwtTokenUtil;
        this.blacklistService = blacklistService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        // Populate MDC with a unique request ID for correlated tracing
        String requestId = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        MDC.put("requestId", requestId);
        response.setHeader("X-Request-Id", requestId);

        try {
            final String authHeader = request.getHeader("Authorization");

            String username = null;
            String jwtToken = null;
            String jti      = null;

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                jwtToken = authHeader.substring(7);
                try {
                    username = jwtTokenUtil.getUsernameFromToken(jwtToken);
                    jti      = jwtTokenUtil.getJtiFromToken(jwtToken);
                } catch (IllegalArgumentException e) {
                    LOG.error("Unable to get JWT Token: {}", e.getMessage());
                } catch (ExpiredJwtException e) {
                    LOG.warn("JWT Token has expired for request: {}", request.getRequestURI());
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"Token has expired. Please refresh.\"}");
                    return;
                } catch (Exception e) {
                    LOG.error("JWT parse error: {}", e.getMessage());
                }
            }

            // ── Blacklist check ───────────────────────────────────────────
            if (jti != null && blacklistService.isBlacklisted(jti)) {
                LOG.warn("Rejected blacklisted token jti={} for request: {}", jti, request.getRequestURI());
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Token has been revoked. Please log in again.\"}");
                return;
            }

            // ── Authenticate ──────────────────────────────────────────────
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                MDC.put("userId", username);

                try {
                    UserDetails userDetails = this.authService.loadUserByUsername(username);

                    if (jwtTokenUtil.validateToken(jwtToken, userDetails)) {
                        UsernamePasswordAuthenticationToken authToken =
                                new UsernamePasswordAuthenticationToken(
                                        userDetails, null, userDetails.getAuthorities());
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    }
                } catch (org.springframework.security.core.userdetails.UsernameNotFoundException e) {
                    LOG.warn("UsernameNotFoundException occurred for user: {}", username, e);
                    SecurityContextHolder.clearContext();
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"User not found. Unauthorized.\"}");
                    return;
                }
            }

            chain.doFilter(request, response);

        } finally {
            // Always clear MDC to prevent thread-local leaks on thread pool reuse
            MDC.clear();
        }
    }
}
