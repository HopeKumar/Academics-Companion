package com.lms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.model.DocumentChunk;
import com.lms.model.Source;
import com.lms.model.User;
import com.lms.repository.DocumentChunkRepository;
import com.lms.repository.SourceRepository;
import com.lms.repository.SummaryRepository;
import com.lms.repository.UserRepository;
import com.lms.service.ai.AIOrchestratorService;
import com.lms.dto.ai.SummaryResponse;
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

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
public class SummaryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private DocumentChunkRepository chunkRepository;

    @Autowired
    private SummaryRepository summaryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AIOrchestratorService aiOrchestratorService;

    private User testUser;
    private Source testSource;

    @BeforeEach
    public void setup() {
        chunkRepository.deleteAll();
        sourceRepository.deleteAll();
        summaryRepository.deleteAll();
        userRepository.deleteAll();

        testUser = new User("testuser", "password123", com.lms.model.Role.STUDENT);
        testUser = userRepository.save(testUser);

        testSource = new Source(testUser.getId(), "test.pdf", "PDF", 1024);
        testSource = sourceRepository.save(testSource);

        DocumentChunk chunk = new DocumentChunk(testSource.getId(), testUser.getId(), "This is test document content.", List.of(0.1, 0.2), 1);
        chunkRepository.save(chunk);
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"STUDENT"})
    public void testGenerateSummary() throws Exception {
        SummaryResponse mockResponse = new SummaryResponse("Test Summary", List.of("Point 1"), List.of("Takeaway 1"), List.of(new SummaryResponse.DefinitionEntry("Term", "Def")), List.of("Step 1"));

        Mockito.when(aiOrchestratorService.generateSummary(anyString())).thenAnswer(invocation -> {
            Thread.sleep(500);
            return mockResponse;
        });

        String requestJson = "{ \"sourceId\": \"" + testSource.getId() + "\" }";

        mockMvc.perform(post("/summary/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.type").value("SUMMARY"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        // Test GET endpoint returns 202 when status is PROCESSING (default)
        mockMvc.perform(get("/summary/" + testSource.getId()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PROCESSING"));

        // Wait for the background thread to save the summary
        int attempts = 0;
        while (summaryRepository.findBySourceId(testSource.getId()).isEmpty() && attempts < 40) {
            Thread.sleep(100);
            attempts++;
        }

        // Simulate source status change to COMPLETED/READY so GET does not return 202
        testSource.getMetadata().put("status", "COMPLETED");
        sourceRepository.save(testSource);

        // Test GET endpoint returns 200 when status is COMPLETED
        mockMvc.perform(get("/summary/" + testSource.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary").value("Test Summary"));
    }
}
