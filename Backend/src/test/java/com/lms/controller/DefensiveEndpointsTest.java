package com.lms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.model.Question;
import com.lms.model.ResponseRecord;
import com.lms.model.User;
import com.lms.repository.*;
import com.lms.util.JwtTokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class DefensiveEndpointsTest {

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

    // ── Chat Module Tests ───────────────────────────────────────────────

    @Test
    void chatStream_MissingSession_Returns404() throws Exception {
        Map<String, Object> req = new HashMap<>();
        req.put("sessionId", "non-existent-session");
        req.put("content", "Hello");

        mockMvc.perform(post("/chat/stream")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("ChatSession not found: non-existent-session"));
    }

    @Test
    void chatStream_MissingContent_Returns400() throws Exception {
        Map<String, Object> req = new HashMap<>();
        req.put("sessionId", "some-session");
        // missing content

        mockMvc.perform(post("/chat/stream")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.content").exists());
    }

    @Test
    void chatHistory_MissingSessionId_Returns400() throws Exception {
        mockMvc.perform(get("/chat/history")
                .header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("sessionId is required"));
    }

    @Test
    void chatHistory_InvalidSessionId_Returns404() throws Exception {
        mockMvc.perform(get("/chat/history")
                .param("sessionId", "invalid")
                .header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Session not found"));
    }

    // ── Test Engine Tests ───────────────────────────────────────────────

    @Test
    void testNext_EmptyQuestions_GeneratesStarterQuestions() throws Exception {
        Map<String, Object> req = new HashMap<>();
        req.put("userId", testUser.getId());
        req.put("questions", new ArrayList<>());
        req.put("history", new ArrayList<>());

        mockMvc.perform(post("/test/next")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question").exists())
                .andExpect(jsonPath("$.data.question.id").exists())
                .andExpect(jsonPath("$.data.reason").exists());
    }

    @Test
    void testSimulate_EmptyData_WorksCorrectly() throws Exception {
        Map<String, Object> req = new HashMap<>();
        req.put("userId", testUser.getId());
        req.put("questions", new ArrayList<>());
        req.put("history", new ArrayList<>());

        mockMvc.perform(post("/test/simulate")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nextQuestion").exists());
    }

    @Test
    void testMastery_NoData_CreatesMastery() throws Exception {
        Map<String, Object> req = new HashMap<>();
        req.put("studentId", testUser.getId());
        req.put("topic", "Algorithms");
        req.put("correct", true);

        mockMvc.perform(post("/test/mastery")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.topic").value("Algorithms"))
                .andExpect(jsonPath("$.data.studentId").value(testUser.getId()))
                .andExpect(jsonPath("$.data.accuracy").exists());
    }

    // ── Flashcard Review Tests ──────────────────────────────────────────

    @Test
    void reviewFlashcard_MissingCard_Returns404() throws Exception {
        Map<String, Object> req = new HashMap<>();
        req.put("cardId", "non-existent-card");
        req.put("quality", 5);

        mockMvc.perform(post("/flashcards/review")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Flashcard not found: non-existent-card"));
    }

}
