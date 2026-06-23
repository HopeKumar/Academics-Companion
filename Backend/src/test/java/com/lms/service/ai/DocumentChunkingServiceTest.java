package com.lms.service.ai;

import com.lms.dto.ai.DocumentChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DocumentChunkingServiceTest {

    private DocumentChunkingService chunkingService;

    @BeforeEach
    void setUp() {
        chunkingService = new DocumentChunkingService();
    }

    @Test
    void testEmptyOrNullText() {
        assertTrue(chunkingService.chunkDocument(null, "doc1").isEmpty());
        assertTrue(chunkingService.chunkDocument("", "doc1").isEmpty());
        assertTrue(chunkingService.chunkDocument("   ", "doc1").isEmpty());
    }

    @Test
    void testSingleSmallParagraph() {
        String text = "This is a small paragraph that fits easily in one chunk.";
        List<DocumentChunk> chunks = chunkingService.chunkDocument(text, "doc1");
        
        assertEquals(1, chunks.size());
        assertEquals(text, chunks.get(0).content());
        assertEquals("doc1", chunks.get(0).sourceId());
        assertEquals(0, chunks.get(0).chunkIndex());
        assertNotNull(chunks.get(0).id());
    }

    @Test
    void testLargeDocumentChunking() {
        StringBuilder sb = new StringBuilder();
        // Generate enough text to exceed 1200 characters
        for (int i = 0; i < 20; i++) {
            sb.append("This is a sufficiently long sentence number ").append(i).append(" to help us test the chunking logic of the system. ");
            sb.append("We need to make sure that paragraphs and headings are preserved.\n\n");
        }
        String text = sb.toString();
        
        List<DocumentChunk> chunks = chunkingService.chunkDocument(text, "doc1");
        
        assertTrue(chunks.size() > 1);
        
        // Verify overlap logic implicitly by checking first chunk size
        DocumentChunk firstChunk = chunks.get(0);
        assertTrue(firstChunk.content().length() >= 800 && firstChunk.content().length() <= 1300);
        
        DocumentChunk secondChunk = chunks.get(1);
        // The second chunk should contain some overlapping text from the first chunk
        // Let's check if the start of the second chunk exists in the first chunk
        String secondChunkStart = secondChunk.content().substring(0, 50);
        assertTrue(firstChunk.content().contains(secondChunkStart));
    }
}
