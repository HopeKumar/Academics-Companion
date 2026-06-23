package com.lms.service.ai;

import com.lms.dto.ai.DocumentChunk;
import com.lms.model.EmbeddingEntity;
import com.lms.repository.EmbeddingRepository;
import com.lms.service.ai.provider.EmbeddingProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EmbeddingServiceTest {

    @Mock
    private EmbeddingProvider embeddingProvider;
    @Mock
    private DocumentChunkingService documentChunkingService;
    @Mock
    private EmbeddingRepository embeddingRepository;

    @InjectMocks
    private EmbeddingService embeddingService;

    @Test
    void testGenerateEmbedding() {
        List<Double> mockEmbed = List.of(0.1, 0.2, 0.3);
        when(embeddingProvider.getEmbedding(anyString())).thenReturn(mockEmbed);

        List<Double> result = embeddingService.generateEmbedding("Hello world");
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals(0.1, result.get(0));
    }

    @Test
    void testProcessAndStoreEmbeddings() {
        String sourceId = UUID.randomUUID().toString();
        String text = "Sample text for embedding";

        List<DocumentChunk> chunks = List.of(
                new DocumentChunk(UUID.randomUUID(), sourceId, 0, "Sample text")
        );
        when(documentChunkingService.chunkDocument(text, sourceId)).thenReturn(chunks);

        List<Double> mockEmbed = List.of(0.1, 0.2, 0.3);
        when(embeddingProvider.getEmbedding(anyString())).thenReturn(mockEmbed);

        doNothing().when(embeddingRepository).deleteBySourceId(sourceId);
        when(embeddingRepository.saveAll(any())).thenReturn(List.of(new EmbeddingEntity()));

        embeddingService.processAndStoreEmbeddings(text, sourceId);

        verify(embeddingRepository, times(1)).deleteBySourceId(sourceId);
        verify(embeddingRepository, times(1)).saveAll(any());
        verify(embeddingProvider, times(1)).getEmbedding(anyString());
    }
}
