package com.lms.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.model.FlashcardDeck;
import com.lms.model.User;
import com.lms.repository.*;
import com.lms.util.JwtTokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.lms.service.MinioStorageService;
import com.lms.service.ai.AIOrchestratorService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class FeatureIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private ResponseRecordRepository responseRecordRepository;

    @Autowired
    private TopicMasteryRepository topicMasteryRepository;

    @Autowired
    private FlashcardDeckRepository deckRepository;

    @Autowired
    private FlashcardRepository flashcardRepository;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @MockBean
    private MinioStorageService minioStorageService;

    @MockBean
    private AIOrchestratorService aiOrchestratorService;

    private String token;
    private User testUser;

    @BeforeEach
    void setUp() {
        chatSessionRepository.deleteAll();
        chatMessageRepository.deleteAll();
        questionRepository.deleteAll();
        responseRecordRepository.deleteAll();
        topicMasteryRepository.deleteAll();
        deckRepository.deleteAll();
        flashcardRepository.deleteAll();
        userRepository.deleteAll();

        testUser = new User("testuser", "password", com.lms.model.Role.STUDENT);
        testUser = userRepository.save(testUser);

        org.springframework.security.core.userdetails.UserDetails ud =
                new org.springframework.security.core.userdetails.User(
                        testUser.getUsername(), testUser.getPasswordHash(), java.util.Collections.emptyList()
                );

        token = "Bearer " + jwtTokenUtil.generateToken(ud);
    }

    @Test
    void chatStream_NullSession_Returns400() throws Exception {
        Map<String, Object> req = new HashMap<>();
        req.put("content", "Hello");

        mockMvc.perform(post("/chat/stream")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("sessionId is required"));
    }

    @Test
    void chatHistory_NullSessionId_Returns400() throws Exception {
        mockMvc.perform(get("/chat/history")
                .header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("sessionId is required"));
    }

    @Test
    void flashcardReview_MissingCardId_Returns400() throws Exception {
        Map<String, Object> req = new HashMap<>();
        req.put("quality", 5);

        mockMvc.perform(post("/flashcards/review")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("cardId is required"));
    }

    @Test
    void flashcardReview_InvalidQuality_Returns400() throws Exception {
        Map<String, Object> req = new HashMap<>();
        req.put("cardId", "some-card-id");
        req.put("quality", 6); // Invalid quality

        mockMvc.perform(post("/flashcards/review")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("quality must be between 0 and 5"));
    }
}
