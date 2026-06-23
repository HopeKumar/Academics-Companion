package com.lms.controller;

import com.lms.model.ChatSession;
import com.lms.model.User;
import com.lms.repository.ChatSessionRepository;
import com.lms.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;
import org.springframework.boot.test.mock.mockito.MockBean;
import reactor.core.publisher.Flux;

@SpringBootTest
@AutoConfigureMockMvc
public class StreamChatIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    private User testUser;
    private ChatSession testSession;

    @MockBean
    private com.lms.service.ai.provider.MistralProvider mistralProvider;

    @MockBean
    private com.lms.service.MinioStorageService minioStorageService;

    @BeforeEach
    public void setup() {
        chatSessionRepository.deleteAll();
        userRepository.deleteAll();

        testUser = new User("testuser", "password123", com.lms.model.Role.STUDENT);
        testUser = userRepository.save(testUser);

        testSession = new ChatSession(testUser.getId(), "Test Chat");
        testSession = chatSessionRepository.save(testSession);
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"STUDENT"})
    public void testStreamChatOutput() throws Exception {
        when(mistralProvider.generateStream(anyString(), anyMap()))
            .thenReturn(Flux.just("Hello ", "AI"));

        String requestJson = "{ \"sessionId\": \"" + testSession.getId() + "\", \"content\": \"Hello AI\" }";

        MvcResult result = mockMvc.perform(post("/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk())
                .andReturn();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM_VALUE));
        
        String responseContent = result.getResponse().getContentAsString();
        System.out.println("STREAM CHAT RESPONSE CONTENT: '" + responseContent + "'");
        assertTrue(responseContent.contains("event:done") || responseContent.contains("data:[DONE]"));
    }
}
