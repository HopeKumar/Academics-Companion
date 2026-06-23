package com.lms.controller;

import com.lms.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.lms.service.AuthService;
import com.lms.util.JwtTokenUtil;
import com.lms.service.TokenBlacklistService;
import com.lms.config.SecurityConfig;
import com.lms.config.JwtRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WebMvcTest(UploadController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("Global Exception Handler - REST API Tests")
@AutoConfigureMockMvc(addFilters = false)
class GlobalExceptionHandlerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private AuthService authService;
    @MockBean private JwtTokenUtil jwtTokenUtil;
    @MockBean private TokenBlacklistService blacklistService;
    @MockBean private com.lms.metrics.MetricsRegistry metricsRegistry;
    @org.springframework.boot.test.context.TestConfiguration
    static class TestConfig {
        @org.springframework.context.annotation.Bean
        public JwtRequestFilter jwtRequestFilter(AuthService authService,
                                                 JwtTokenUtil jwtTokenUtil,
                                                 TokenBlacklistService blacklistService) {
            return new JwtRequestFilter(authService, jwtTokenUtil, blacklistService) {
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

    @MockBean private com.lms.service.SourceService sourceService; // required by UploadController



    @Test
    @DisplayName("Upload without file should return 400 Bad Request")
    void testMissingServletRequestParameterException() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/sources/upload"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.error").exists())
               .andExpect(jsonPath("$.status").value("FAILED"));
    }
}
