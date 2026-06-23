package com.lms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.model.DocumentChunk;
import com.lms.model.Source;
import com.lms.model.User;
import com.lms.repository.DocumentChunkRepository;
import com.lms.repository.SourceRepository;
import com.lms.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class DocumentChunkControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private DocumentChunkRepository chunkRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private User testUser;
    private Source testSource;

    @BeforeEach
    public void setup() {
        chunkRepository.deleteAll();
        sourceRepository.deleteAll();
        userRepository.deleteAll();

        testUser = new User("testuser", "password123", com.lms.model.Role.STUDENT);
        testUser = userRepository.save(testUser);

        testSource = new Source(testUser.getId(), "test_doc.pdf", "PDF", 2048);
        testSource = sourceRepository.save(testSource);

        DocumentChunk c1 = new DocumentChunk(testSource.getId(), testUser.getId(), "First chunk of content.", null, 1, 0, "2026-06-15T00:00:00Z");
        DocumentChunk c2 = new DocumentChunk(testSource.getId(), testUser.getId(), "Second chunk containing keyword.", null, 1, 1, "2026-06-15T00:00:00Z");
        chunkRepository.save(c1);
        chunkRepository.save(c2);
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"STUDENT"})
    public void testGetChunksSuccess() throws Exception {
        mockMvc.perform(get("/sources/" + testSource.getId() + "/chunks")
                .param("page", "0")
                .param("size", "10")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].content").value("First chunk of content."))
                .andExpect(jsonPath("$.data.content[0].chunkIndex").value(0))
                .andExpect(jsonPath("$.data.content[1].content").value("Second chunk containing keyword."))
                .andExpect(jsonPath("$.data.content[1].chunkIndex").value(1));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"STUDENT"})
    public void testGetChunksWithSearch() throws Exception {
        mockMvc.perform(get("/sources/" + testSource.getId() + "/chunks")
                .param("q", "keyword")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].content").value("Second chunk containing keyword."));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"STUDENT"})
    public void testGetChunksNotFound() throws Exception {
        mockMvc.perform(get("/sources/invalid-source-id/chunks")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Source not found: invalid-source-id"));
    }

    @Test
    @WithMockUser(username = "otheruser", roles = {"STUDENT"})
    public void testGetChunksUnauthorized() throws Exception {
        // Create other user
        User otherUser = new User("otheruser", "password123", com.lms.model.Role.STUDENT);
        userRepository.save(otherUser);

        mockMvc.perform(get("/sources/" + testSource.getId() + "/chunks")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Unauthorized to access this source"));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"STUDENT"})
    public void testGetReaderDataSuccess() throws Exception {
        mockMvc.perform(get("/sources/" + testSource.getId() + "/reader")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sourceId").value(testSource.getId()))
                .andExpect(jsonPath("$.data.title").value("test_doc.pdf"))
                .andExpect(jsonPath("$.data.pages[0].page").value(1))
                .andExpect(jsonPath("$.data.pages[0].content").value("First chunk of content.\n\nSecond chunk containing keyword."));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"STUDENT"})
    public void testGetReaderDataNotFound() throws Exception {
        mockMvc.perform(get("/sources/invalid-source-id/reader")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}
