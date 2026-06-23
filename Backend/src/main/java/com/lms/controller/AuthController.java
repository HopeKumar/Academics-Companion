package com.lms.controller;

import com.lms.dto.auth.LoginRequest;
import com.lms.dto.auth.RegisterRequest;
import com.lms.dto.auth.RefreshRequest;
import com.lms.dto.auth.AuthResponse;
import com.lms.model.Role;
import com.lms.model.User;
import com.lms.service.AuthService;
import com.lms.service.AuditLogService;
import com.lms.service.TokenBlacklistService;
import com.lms.util.JwtTokenUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import com.lms.metrics.MetricsRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * Authentication endpoints.
 *
 * Production hardening applied:
 *   - POST /auth/logout — blacklists the current access token JTI.
 *   - POST /auth/refresh — validates refresh token, issues new access token.
 *   - DTOs remain as inner static classes here (moved to dto/ package in Phase 11).
 */
@RestController
@RequestMapping({"/auth", "/api/v1/auth"})
public class AuthController {

    private static final Logger LOG = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager  authenticationManager;
    private final AuthService            authService;
    private final JwtTokenUtil           jwtTokenUtil;
    private final TokenBlacklistService  blacklistService;
    private final AuditLogService        auditLogService;
    private final MetricsRegistry        metricsRegistry;

    @Autowired
    public AuthController(AuthenticationManager authenticationManager,
                          AuthService authService,
                          JwtTokenUtil jwtTokenUtil,
                          TokenBlacklistService blacklistService,
                          AuditLogService auditLogService,
                          MetricsRegistry metricsRegistry) {
        this.authenticationManager = authenticationManager;
        this.authService           = authService;
        this.jwtTokenUtil          = jwtTokenUtil;
        this.blacklistService      = blacklistService;
        this.auditLogService       = auditLogService;
        this.metricsRegistry       = metricsRegistry;
    }

    // ── POST /auth/register ───────────────────────────────────────────────

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest request,
                                          HttpServletRequest httpRequest) {
        try {
            User registeredUser = authService.register(
                    request.getUsername(),
                    request.getPassword(),
                    request.getRole()
            );
            LOG.info("User registered successfully: username={}", registeredUser.getUsername());
            auditLogService.log(registeredUser.getId(), "USER_REGISTER", registeredUser.getId(), 
                    "Registered user with role " + registeredUser.getRole(), httpRequest.getRemoteAddr());
            metricsRegistry.recordAuth("register", "success");
            return ResponseEntity.ok(Map.of(
                    "message",  "User registered successfully",
                    "userId",   registeredUser.getId(),
                    "username", registeredUser.getUsername()
            ));
        } catch (IllegalArgumentException e) {
            metricsRegistry.recordAuth("register", "failure");
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            LOG.error("Unexpected error during registration", e);
            metricsRegistry.recordAuth("register", "failure");
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "An unexpected error occurred during registration"));
        }
    }

    // ── POST /auth/login ──────────────────────────────────────────────────

    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@Valid @RequestBody LoginRequest request,
                                       HttpServletRequest httpRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();

            String accessToken  = jwtTokenUtil.generateToken(userDetails);
            String refreshToken = jwtTokenUtil.generateRefreshToken(userDetails);

            User user = authService.getUserByUsername(request.getUsername());
            LOG.info("User logged in: username={} ip={}", request.getUsername(), httpRequest.getRemoteAddr());
            auditLogService.log(user.getId(), "USER_LOGIN", user.getId(), 
                    "Successful login", httpRequest.getRemoteAddr());

            metricsRegistry.recordAuth("login", "success");
            return ResponseEntity.ok(new AuthResponse(
                    accessToken,
                    refreshToken,
                    user.getUsername(),
                    user.getRole().name(),
                    user.getId()
            ));
        } catch (Exception e) {
            LOG.warn("Login failed for username={} ip={}",
                    request.getUsername(), httpRequest.getRemoteAddr());
            metricsRegistry.recordAuth("login", "failure");
            return ResponseEntity.status(401).body(Map.of("error", "Invalid username or password"));
        }
    }

    // ── POST /auth/refresh ────────────────────────────────────────────────

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody RefreshRequest request) {
        String refreshToken = request.getRefreshToken();
        if (refreshToken == null || refreshToken.isBlank()) {
            metricsRegistry.recordAuth("refresh", "failure");
            return ResponseEntity.badRequest().body(Map.of("error", "Refresh token is required"));
        }
        try {
            String username = jwtTokenUtil.getUsernameFromToken(refreshToken);
            UserDetails userDetails = authService.loadUserByUsername(username);

            if (!jwtTokenUtil.validateRefreshToken(refreshToken, userDetails)) {
                metricsRegistry.recordAuth("refresh", "failure");
                return ResponseEntity.status(401)
                        .body(Map.of("error", "Invalid or expired refresh token"));
            }

            String newAccessToken = jwtTokenUtil.generateToken(userDetails);
            LOG.info("Access token refreshed for username={}", username);

            metricsRegistry.recordAuth("refresh", "success");
            return ResponseEntity.ok(new AuthResponse(newAccessToken));
        } catch (Exception e) {
            LOG.warn("Refresh token validation failed: {}", e.getMessage());
            metricsRegistry.recordAuth("refresh", "failure");
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Invalid or expired refresh token"));
        }
    }

    // ── POST /auth/logout ─────────────────────────────────────────────────

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        final String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                String jti    = jwtTokenUtil.getJtiFromToken(token);
                Date   expiry = jwtTokenUtil.getExpirationDateFromToken(token);
                blacklistService.blacklist(jti, expiry.toInstant());
                String username = jwtTokenUtil.getUsernameFromToken(token);
                LOG.info("User logged out: username={} jti={}", username, jti);
                User user = authService.getUserByUsername(username);
                if (user != null) {
                    auditLogService.log(user.getId(), "USER_LOGOUT", user.getId(), 
                            "Successful logout for JTI: " + jti, request.getRemoteAddr());
                }
            } catch (Exception e) {
                LOG.warn("Logout: could not parse token JTI — token may already be invalid: {}", e.getMessage());
            }
        }
        metricsRegistry.recordAuth("logout", "success");
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    // ── GET /auth/me ──────────────────────────────────────────────────────

    @GetMapping("/me")
    public ResponseEntity<?> getMe() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() instanceof String) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        UserDetails userDetails = (UserDetails) auth.getPrincipal();
        User user = authService.getUserByUsername(userDetails.getUsername());

        return ResponseEntity.ok(Map.of(
                "id",        user.getId(),
                "username",  user.getUsername(),
                "role",      user.getRole().name(),
                "createdAt", user.getCreatedAt()
        ));
    }

    // DTO classes moved to com.lms.dto.auth package
}
