package com.lms.controller;

import com.lms.model.User;
import com.lms.model.Podcast;
import com.lms.repository.UserRepository;
import com.lms.repository.PodcastRepository;
import com.lms.service.FileStorageService;
import com.lms.service.MinioStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class FileExportIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PodcastRepository podcastRepository;

    @Autowired
    private MinioStorageService minioStorageService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private FileStorageService fileStorageService;

    private String token;

    @BeforeEach
    void setup() throws Exception {
        userRepository.deleteAll();
        User user = new User();
        user.setUsername("testuser");
        user.setRole(com.lms.model.Role.STUDENT);
        user.setPasswordHash(passwordEncoder.encode("password123"));
        userRepository.save(user);

        String loginResponse = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"testuser\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        token = com.jayway.jsonpath.JsonPath.read(loginResponse, "$.data.accessToken");
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    @Test
    void testMissingFileReturns404() throws Exception {
        mockMvc.perform(get("/podcasts/unknown123/audio")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void testPodcastAudioPlayback() throws Exception {
        User user = userRepository.findByUsername("testuser").orElseThrow();

        // Create and save Podcast
        Podcast podcast = new Podcast();
        podcast.setUserId(user.getId());
        podcast.setTopic("Test Topic");
        podcast.setSourceId("test-source");
        podcast.setStatus(com.lms.model.PodcastJobStatus.COMPLETED);
        podcast.setAudioStatus(com.lms.model.PodcastJobStatus.COMPLETED);
        podcast.setAudioFile("podcasts/test-podcast-audio.wav");
        podcast.setAudioUrl("/podcasts/test-podcast/audio");
        podcast = podcastRepository.save(podcast);

        // Upload a dummy WAV file
        byte[] dummyWav = new byte[] {
            0x52, 0x49, 0x46, 0x46, 0x24, 0x00, 0x00, 0x00, 
            0x57, 0x41, 0x56, 0x45, 0x66, 0x6d, 0x74, 0x20, 
            0x10, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 
            0x44, (byte)0xac, 0x00, 0x00, (byte)0x88, 0x58, 0x01, 0x00, 
            0x02, 0x00, 0x10, 0x00, 0x64, 0x61, 0x74, 0x61, 
            0x00, 0x00, 0x00, 0x00
        };
        Path tempFile = Files.createTempFile("test-dummy-", ".wav");
        Files.write(tempFile, dummyWav);
        minioStorageService.uploadLocalFile("podcasts/test-podcast-audio.wav", tempFile, "audio/wav");
        Files.deleteIfExists(tempFile);

        try {
            mockMvc.perform(get("/podcasts/" + podcast.getId() + "/audio")
                    .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        } finally {
            podcastRepository.delete(podcast);
            try {
                minioStorageService.deleteFile("podcasts/test-podcast-audio.wav");
            } catch (Exception ignored) {}
        }
    }

    @Test
    void testPathTraversalProtection() {
        // fileStorageService should throw SecurityException on traversal
        assertThrows(SecurityException.class, () -> {
            fileStorageService.getPath("podcasts", "../../../etc/passwd");
        });
    }

    @Test
    void testUnauthorizedAccess() throws Exception {
        mockMvc.perform(get("/slides/some-slide-id/download"))
                .andExpect(status().isForbidden());
    }
}
