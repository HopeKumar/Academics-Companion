package com.lms.service;

import com.lms.dto.DocumentChunkResponse;
import com.lms.dto.DocumentReaderResponse;
import com.lms.exception.ResourceNotFoundException;
import com.lms.model.DocumentChunk;
import com.lms.model.Source;
import com.lms.repository.DocumentChunkRepository;
import com.lms.repository.SourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DocumentChunkServiceTest {

    @Mock
    private SourceRepository sourceRepository;

    @Mock
    private DocumentChunkRepository chunkRepository;

    @InjectMocks
    private DocumentChunkService chunkService;

    private Source testSource;
    private String userId = "user-123";
    private String sourceId = "source-123";

    @BeforeEach
    void setUp() {
        testSource = new Source();
        testSource.setId(sourceId);
        testSource.setUserId(userId);
        testSource.setName("test_document.pdf");
    }

    @Test
    void testValidateSourceAccessSuccess() {
        when(sourceRepository.findById(sourceId)).thenReturn(Optional.of(testSource));

        Source result = chunkService.validateSourceAccess(sourceId, userId);

        assertNotNull(result);
        assertEquals(sourceId, result.getId());
        assertEquals(userId, result.getUserId());
    }

    @Test
    void testValidateSourceAccessNotFound() {
        when(sourceRepository.findById(sourceId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> {
            chunkService.validateSourceAccess(sourceId, userId);
        });
    }

    @Test
    void testValidateSourceAccessUnauthorized() {
        testSource.setUserId("other-user");
        when(sourceRepository.findById(sourceId)).thenReturn(Optional.of(testSource));

        assertThrows(SecurityException.class, () -> {
            chunkService.validateSourceAccess(sourceId, userId);
        });
    }

    @Test
    void testGetChunksWithoutQuery() {
        when(sourceRepository.findById(sourceId)).thenReturn(Optional.of(testSource));

        List<DocumentChunk> chunks = List.of(
                new DocumentChunk(sourceId, userId, "Text 1", null, 1, 0, "2026-06-15T00:00:00Z"),
                new DocumentChunk(sourceId, userId, "Text 2", null, 1, 1, "2026-06-15T00:00:00Z")
        );
        chunks.get(0).setId("chunk-1");
        chunks.get(1).setId("chunk-2");

        Page<DocumentChunk> chunkPage = new PageImpl<>(chunks, PageRequest.of(0, 50), chunks.size());
        when(chunkRepository.findBySourceId(eq(sourceId), any(Pageable.class))).thenReturn(chunkPage);
        when(chunkRepository.findBySourceId(sourceId)).thenReturn(chunks); // For dynamic index mapping calculation

        Page<DocumentChunkResponse> result = chunkService.getChunks(sourceId, userId, null, PageRequest.of(0, 50));

        assertNotNull(result);
        assertEquals(2, result.getTotalElements());
        assertEquals("Text 1", result.getContent().get(0).content());
        assertEquals(0, result.getContent().get(0).chunkIndex());
        assertEquals(1, result.getContent().get(1).chunkIndex());
    }

    @Test
    void testGetChunksWithQuery() {
        when(sourceRepository.findById(sourceId)).thenReturn(Optional.of(testSource));

        List<DocumentChunk> chunks = List.of(
                new DocumentChunk(sourceId, userId, "Specific keyword test", null, 1, 0, "2026-06-15T00:00:00Z")
        );
        chunks.get(0).setId("chunk-1");

        Page<DocumentChunk> chunkPage = new PageImpl<>(chunks, PageRequest.of(0, 50), chunks.size());
        when(chunkRepository.findBySourceIdAndTextContainingIgnoreCase(eq(sourceId), eq("keyword"), any(Pageable.class)))
                .thenReturn(chunkPage);
        when(chunkRepository.findBySourceId(sourceId)).thenReturn(chunks); // For dynamic index mapping calculation

        Page<DocumentChunkResponse> result = chunkService.getChunks(sourceId, userId, "keyword", PageRequest.of(0, 50));

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("Specific keyword test", result.getContent().get(0).content());
    }

    @Test
    void testGetChunksRetroactiveFallback() {
        when(sourceRepository.findById(sourceId)).thenReturn(Optional.of(testSource));

        // Create older chunks with chunkIndex = 0
        DocumentChunk c1 = new DocumentChunk(sourceId, userId, "Chunk Page 1 Index 0", null, 1, 0, "2026-06-15T00:00:00Z");
        c1.setId("chunk-first");
        DocumentChunk c2 = new DocumentChunk(sourceId, userId, "Chunk Page 1 Index 1", null, 1, 0, "2026-06-15T00:00:00Z");
        c2.setId("chunk-second");

        List<DocumentChunk> allChunks = List.of(c1, c2);

        Page<DocumentChunk> chunkPage = new PageImpl<>(allChunks, PageRequest.of(0, 50), allChunks.size());
        when(chunkRepository.findBySourceId(eq(sourceId), any(Pageable.class))).thenReturn(chunkPage);
        when(chunkRepository.findBySourceId(sourceId)).thenReturn(allChunks);

        Page<DocumentChunkResponse> result = chunkService.getChunks(sourceId, userId, null, PageRequest.of(0, 50));

        assertNotNull(result);
        assertEquals(2, result.getTotalElements());
        // Since original chunkIndex was 0, it falls back to dynamic index mapping
        // sorting by pageNumber then ID ("chunk-first" vs "chunk-second")
        assertEquals(0, result.getContent().get(0).chunkIndex());
        assertEquals(1, result.getContent().get(1).chunkIndex());
    }

    @Test
    void testGetReaderData() {
        when(sourceRepository.findById(sourceId)).thenReturn(Optional.of(testSource));

        List<DocumentChunk> chunks = new ArrayList<>();
        DocumentChunk c1 = new DocumentChunk(sourceId, userId, "Hello World from Page 1.", null, 1, 0, "2026-06-15T00:00:00Z");
        c1.setId("c1");
        DocumentChunk c2 = new DocumentChunk(sourceId, userId, "More Page 1 content.", null, 1, 1, "2026-06-15T00:00:00Z");
        c2.setId("c2");
        DocumentChunk c3 = new DocumentChunk(sourceId, userId, "This is Page 2 content.", null, 2, 0, "2026-06-15T00:00:00Z");
        c3.setId("c3");

        chunks.add(c2); // Out of order to verify sorting
        chunks.add(c1);
        chunks.add(c3);

        when(chunkRepository.findBySourceId(sourceId)).thenReturn(chunks);

        DocumentReaderResponse response = chunkService.getReaderData(sourceId, userId);

        assertNotNull(response);
        assertEquals(sourceId, response.sourceId());
        assertEquals("test_document.pdf", response.title());
        assertEquals(2, response.pages().size());

        // Page 1
        DocumentReaderResponse.PageContent page1 = response.pages().get(0);
        assertEquals(1, page1.page());
        assertEquals("Hello World from Page 1.\n\nMore Page 1 content.", page1.content());

        // Page 2
        DocumentReaderResponse.PageContent page2 = response.pages().get(1);
        assertEquals(2, page2.page());
        assertEquals("This is Page 2 content.", page2.content());
    }
}
