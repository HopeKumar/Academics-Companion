package com.lms.service.ai.provider;

import java.util.List;

public interface EmbeddingProvider {
    List<Double> getEmbedding(String text);
}
