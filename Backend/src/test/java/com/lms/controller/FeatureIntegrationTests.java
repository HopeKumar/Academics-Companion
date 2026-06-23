package com.lms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.model.Source;
import com.lms.model.User;
import com.lms.repository.SourceRepository;
import com.lms.repository.UserRepository;
import com.lms.repository.JobStatusRepository;
import com.lms.service.ai.AIOrchestratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@AutoConfigureMockMvc
public class FeatureIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JobStatusRepository jobStatusRepository;

    private User testUser;
    private Source testSource;

    @BeforeEach
    public void setup() {
        sourceRepository.deleteAll();
        userRepository.deleteAll();
        jobStatusRepository.deleteAll();

        testUser = new User("testuser", "password123", com.lms.model.Role.STUDENT);
        testUser = userRepository.save(testUser);

        testSource = new Source(testUser.getId(), "test.pdf", "PDF", 1024);
        testSource.setExtractedText("This is a test document with sufficient text content to generate summaries.");
        testSource = sourceRepository.save(testSource);
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"STUDENT"})
    public void testSseSubscription() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/events/subscribe/" + testSource.getId()))
                .andExpect(status().isOk())
                .andReturn();

        String content = result.getResponse().getContentAsString();
        assertTrue(content.contains("event:CONNECTED"));
        assertTrue(content.contains("Subscribed successfully"));
    }
}
