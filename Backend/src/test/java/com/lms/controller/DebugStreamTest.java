package com.lms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.model.ChatSession;
import com.lms.model.User;
import com.lms.repository.ChatSessionRepository;
import com.lms.repository.UserRepository;
import com.lms.util.JwtTokenUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
public class DebugStreamTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Test
    void debugChatStream() throws Exception {
        userRepository.deleteAll();
        chatSessionRepository.deleteAll();

        User testUser = new User("debuguser", "password", com.lms.model.Role.STUDENT);
        testUser = userRepository.save(testUser);

        org.springframework.security.core.userdetails.UserDetails ud = 
            new org.springframework.security.core.userdetails.User(
                testUser.getUsername(), testUser.getPasswordHash(), java.util.Collections.emptyList()
            );

        String token = "Bearer " + jwtTokenUtil.generateToken(ud);

        ChatSession session = new ChatSession(testUser.getId(), "Test Session");
        session = chatSessionRepository.save(session);

        Map<String, Object> req = new HashMap<>();
        req.put("sessionId", session.getId());
        req.put("content", "Hello");

        MvcResult result = mockMvc.perform(post("/chat/stream")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andReturn();

        System.out.println("STATUS: " + result.getResponse().getStatus());
        System.out.println("BODY: " + result.getResponse().getContentAsString());
        if (result.getResolvedException() != null) {
            result.getResolvedException().printStackTrace();
        }
    }
}
