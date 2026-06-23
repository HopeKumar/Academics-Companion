package com.lms;

import com.lms.service.ai.provider.EmbeddingProvider;

import java.util.List;

/**
 * Deterministic fake embedding provider for unit tests.
 *
 * Returns a configurable fixed embedding vector. Use TestFixtures.testEmbedding()
 * for a standard 4-dim unit vector, or set a custom vector per-test.
 */
public class MockEmbeddingProvider implements EmbeddingProvider {

    private List<Double> nextEmbedding = TestFixtures.testEmbedding();

    public void setNextEmbedding(List<Double> embedding) {
        this.nextEmbedding = embedding;
    }

    @Override
    public List<Double> getEmbedding(String text) {
        return nextEmbedding;
    }
}
