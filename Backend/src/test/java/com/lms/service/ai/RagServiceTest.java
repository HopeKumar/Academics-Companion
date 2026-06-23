package com.lms.service.ai;

import com.lms.dto.ai.ChatResponse;
import com.lms.model.EmbeddingEntity;
import com.lms.repository.EmbeddingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class RagServiceTest {

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private AIOrchestratorService aiOrchestratorService;

    @Mock
    private EmbeddingRepository embeddingRepository;

    @InjectMocks
    private RagService ragService;

    private String sourceId;
    private String question;

    @BeforeEach
    void setUp() {
        sourceId = UUID.randomUUID().toString();
        question = "What is RAG?";
    }

    @Test
    void testRetrieveRelevantChunks() {
        List<Double> mockEmbed = List.of(0.1, 0.2);
        when(embeddingService.generateEmbedding(question)).thenReturn(mockEmbed);
        
        EmbeddingEntity entity = new EmbeddingEntity();
        entity.setContent("RAG is Retrieval Augmented Generation.");
        entity.setEmbedding(List.of(0.1, 0.2));
        when(embeddingRepository.findBySourceId(eq(sourceId)))
                .thenReturn(List.of(entity));

        List<EmbeddingEntity> chunks = ragService.retrieveRelevantChunks(sourceId, question, 5);
        assertEquals(1, chunks.size());
        assertEquals("RAG is Retrieval Augmented Generation.", chunks.get(0).getContent());
    }

    @Test
    void testAnswerQuestionWithChunks() {
        List<Double> mockEmbed = List.of(0.1, 0.2);
        when(embeddingService.generateEmbedding(question)).thenReturn(mockEmbed);
        
        EmbeddingEntity entity = new EmbeddingEntity();
        entity.setContent("RAG is Retrieval Augmented Generation.");
        entity.setEmbedding(List.of(0.1, 0.2));
        when(embeddingRepository.findBySourceId(eq(sourceId)))
                .thenReturn(List.of(entity));

        when(aiOrchestratorService.chat(eq(question), anyString())).thenReturn(new ChatResponse("Answer based on RAG."));

        ChatResponse response = ragService.answerQuestion(sourceId, question);
        assertEquals("Answer based on RAG.", response.reply());
    }

    @Test
    void testAnswerQuestionNoChunksFound() {
        List<Double> mockEmbed = List.of(0.1, 0.2);
        when(embeddingService.generateEmbedding(question)).thenReturn(mockEmbed);
        
        when(embeddingRepository.findBySourceId(eq(sourceId)))
                .thenReturn(Collections.emptyList());

        ChatResponse response = ragService.answerQuestion(sourceId, question);
        assertEquals("I could not find this information in the uploaded material.", response.reply());
    }
}
