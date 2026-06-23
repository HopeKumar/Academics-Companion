package com.lms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.TestFixtures;
import com.lms.model.User;
import com.lms.service.AuthService;
import com.lms.service.TokenBlacklistService;
import com.lms.service.AuditLogService;
import com.lms.util.JwtTokenUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer tests for AuthController.
 * Uses @WebMvcTest to load only the web layer (no full context).
 */
@WebMvcTest(AuthController.class)
@Import(com.lms.config.SecurityConfig.class)
@DisplayName("AuthController — REST API Tests")
class AuthControllerTest {

    @Autowired private MockMvc     mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private AuthenticationManager  authenticationManager;
    @MockBean private AuthService            authService;
    @MockBean private JwtTokenUtil           jwtTokenUtil;
    @MockBean private TokenBlacklistService  blacklistService;
    @MockBean private AuditLogService        auditLogService;
    @MockBean private com.lms.metrics.MetricsRegistry metricsRegistry;

    @org.springframework.boot.test.context.TestConfiguration
    static class TestConfig {
        @org.springframework.context.annotation.Bean
        public com.lms.config.JwtRequestFilter jwtRequestFilter(AuthService authService,
                                                 JwtTokenUtil jwtTokenUtil,
                                                 TokenBlacklistService blacklistService) {
            return new com.lms.config.JwtRequestFilter(authService, jwtTokenUtil, blacklistService) {
                @Override
                protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,
                                                jakarta.servlet.http.HttpServletResponse response,
                                                jakarta.servlet.FilterChain chain)
                        throws jakarta.servlet.ServletException, java.io.IOException {
                    chain.doFilter(request, response);
                }
            };
        }
    }

    private User testUser;

    @BeforeEach
    void setUp() throws Exception {
        testUser = TestFixtures.studentUser();
    }

    // ── POST /auth/register ───────────────────────────────────────────────

    @Test
    @DisplayName("POST /auth/register — valid request returns 200 with userId")
    void registerValidUser() throws Exception {
        when(authService.register(anyString(), anyString(), any()))
                .thenReturn(testUser);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "testStudent",
                                "password", "password123"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value("user-student-001"))
                .andExpect(jsonPath("$.data.username").value("testStudent"));
    }

    @Test
    @DisplayName("POST /auth/register — duplicate username returns 400")
    void registerDuplicateUsernameReturnsBadRequest() throws Exception {
        when(authService.register(anyString(), anyString(), any()))
                .thenThrow(new IllegalArgumentException("Username already exists"));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "testStudent",
                                "password", "password123"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("POST /auth/register — blank username returns 400 validation error")
    void registerBlankUsernameReturnsValidationError() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "",
                                "password", "password123"
                        ))))
                .andExpect(status().isBadRequest());
    }

    // ── POST /auth/login ──────────────────────────────────────────────────

    @Test
    @DisplayName("POST /auth/login — valid credentials return tokens")
    void loginValidCredentials() throws Exception {
        UserDetails ud = new org.springframework.security.core.userdetails.User(
                "testStudent", "hashed", Collections.emptyList());

        when(authenticationManager.authenticate(any()))
                .thenReturn(new UsernamePasswordAuthenticationToken(ud, null, Collections.emptyList()));
        when(jwtTokenUtil.generateToken(any())).thenReturn("access-token-123");
        when(jwtTokenUtil.generateRefreshToken(any())).thenReturn("refresh-token-456");
        when(authService.getUserByUsername("testStudent")).thenReturn(testUser);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "testStudent",
                                "password", "password123"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-token-123"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token-456"))
                .andExpect(jsonPath("$.data.username").value("testStudent"));
    }

    @Test
    @DisplayName("POST /auth/login — invalid credentials return 401")
    void loginInvalidCredentials() throws Exception {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "wrongUser",
                                "password", "wrongPass"
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());
    }

    // ── POST /auth/logout ─────────────────────────────────────────────────

    @Test
    @DisplayName("POST /auth/logout — valid Bearer token returns 200 success")
    void logoutWithValidToken() throws Exception {
        when(jwtTokenUtil.getJtiFromToken("valid-token"))
                .thenReturn("jti-123");
        when(jwtTokenUtil.getExpirationDateFromToken("valid-token"))
                .thenReturn(new java.util.Date(System.currentTimeMillis() + 3_600_000));
        when(jwtTokenUtil.getUsernameFromToken("valid-token"))
                .thenReturn("testStudent");

        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));

        verify(blacklistService).blacklist(eq("jti-123"), any());
    }

    @Test
    @DisplayName("POST /auth/logout — no token still returns 200 (idempotent)")
    void logoutWithNoTokenReturns200() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isOk());
    }
}
