package com.lms.unit;

import com.lms.TestFixtures;
import com.lms.model.DocumentChunk;
import com.lms.repository.DocumentChunkRepository;
import com.lms.service.HybridRetrievalService;
import com.lms.MockEmbeddingProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for HybridRetrievalService — cosine similarity, keyword scoring,
 * result ranking, limit enforcement. No Spring context or real MongoDB needed.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HybridRetrievalService — Hybrid Retrieval & Ranking")
class HybridRetrievalServiceTest {

    @Mock private DocumentChunkRepository chunkRepository;

    private MockEmbeddingProvider    embeddingProvider;
    private HybridRetrievalService   service;

    @BeforeEach
    void setUp() {
        embeddingProvider = new MockEmbeddingProvider();
        service = new HybridRetrievalService(
                chunkRepository,
                embeddingProvider,
                Optional.empty(), // no VectorStore in unit tests
                new SimpleMeterRegistry()
        );
    }

    @Test
    @DisplayName("Empty query returns empty list without querying DB")
    void emptyQueryReturnsEmpty() {
        List<DocumentChunk> result = service.retrieve("user-1", "", null, 5);
        assertThat(result).isEmpty();
        verifyNoInteractions(chunkRepository);
    }

    @Test
    @DisplayName("Blank query returns empty list")
    void blankQueryReturnsEmpty() {
        List<DocumentChunk> result = service.retrieve("user-1", "   ", null, 5);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("No candidates in DB → empty results")
    void noCandidatesReturnsEmpty() {
        when(chunkRepository.findByUserId("user-1")).thenReturn(Collections.emptyList());

        List<DocumentChunk> result = service.retrieve("user-1", "java streams", null, 5);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Returns up to limit chunks when candidates exist")
    void returnsUpToLimitChunks() {
        List<DocumentChunk> candidates = List.of(
                TestFixtures.chunk("src-1", "user-1", "Java streams are lazy sequences"),
                TestFixtures.chunk("src-1", "user-1", "Streams enable functional-style operations"),
                TestFixtures.chunk("src-1", "user-1", "Terminal operations like collect and reduce"),
                TestFixtures.chunk("src-1", "user-1", "Intermediate operations like filter and map"),
                TestFixtures.chunk("src-1", "user-1", "Unrelated content about other topics")
        );

        when(chunkRepository.findByUserId("user-1")).thenReturn(candidates);
        // Use the same embedding for query and all chunks → equal cosine scores
        embeddingProvider.setNextEmbedding(TestFixtures.testEmbedding());

        // Add test embedding to each chunk
        candidates.forEach(c -> c.setEmbedding(TestFixtures.testEmbedding()));

        List<DocumentChunk> result = service.retrieve("user-1", "java streams", null, 3);
        assertThat(result).hasSize(3);
    }

    @Test
    @DisplayName("Source ID filter restricts candidates to given sources")
    void sourceIdFilterApplied() {
        DocumentChunk chunk = TestFixtures.chunk("src-2", "user-1", "Source-specific content");
        chunk.setEmbedding(TestFixtures.testEmbedding());

        when(chunkRepository.findBySourceIdIn(List.of("src-2"))).thenReturn(List.of(chunk));

        List<DocumentChunk> result = service.retrieve("user-1", "content", List.of("src-2"), 5);
        assertThat(result).hasSize(1);
        verify(chunkRepository).findBySourceIdIn(List.of("src-2"));
        verify(chunkRepository, never()).findByUserId(any());
    }

    @Test
    @DisplayName("Chunk with matching keyword scores higher than chunk without")
    void keywordMatchChunkRanksHigher() {
        // Two chunks: one matches the query keyword, one doesn't
        DocumentChunk matching = TestFixtures.chunk("src-1", "user-1", "binary search trees are used in BST problems");
        DocumentChunk other    = TestFixtures.chunk("src-1", "user-1", "unrelated content about cooking recipes");

        // Use orthogonal embeddings so keyword score breaks the tie
        matching.setEmbedding(TestFixtures.testEmbedding());
        other.setEmbedding(TestFixtures.orthogonalEmbedding());

        when(chunkRepository.findByUserId("user-1")).thenReturn(List.of(matching, other));
        embeddingProvider.setNextEmbedding(TestFixtures.testEmbedding());

        List<DocumentChunk> result = service.retrieve("user-1", "binary search", null, 2);

        // The matching chunk should be returned first
        assertThat(result).isNotEmpty();
        assertThat(result.get(0).getText()).contains("binary");
    }
}
