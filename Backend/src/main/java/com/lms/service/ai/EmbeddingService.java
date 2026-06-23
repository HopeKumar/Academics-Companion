package com.lms.service.ai;

import com.lms.dto.ai.DocumentChunk;
import com.lms.exception.AIServiceException;
import com.lms.model.EmbeddingEntity;
import com.lms.repository.EmbeddingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class EmbeddingService {

    private static final Logger LOG = LoggerFactory.getLogger(EmbeddingService.class);

    private final com.lms.service.ai.provider.EmbeddingProvider embeddingProvider;
    private final DocumentChunkingService documentChunkingService;
    private final EmbeddingRepository embeddingRepository;

    public EmbeddingService(com.lms.service.ai.provider.EmbeddingProvider embeddingProvider, 
                            DocumentChunkingService documentChunkingService, 
                            EmbeddingRepository embeddingRepository) {
        this.embeddingProvider = embeddingProvider;
        this.documentChunkingService = documentChunkingService;
        this.embeddingRepository = embeddingRepository;
    }

    @Transactional
    public void processAndStoreEmbeddings(String text, String sourceId) {
        LOG.info("Generating and storing embeddings for document {}", sourceId);
        
        embeddingRepository.deleteBySourceId(sourceId);

        List<DocumentChunk> chunks = documentChunkingService.chunkDocument(text, sourceId);
        
        List<EmbeddingEntity> entities = new ArrayList<>();
        for (DocumentChunk chunk : chunks) {
            List<Double> vector = generateEmbedding(chunk.content());
            
            EmbeddingEntity entity = new EmbeddingEntity();
            entity.setSourceId(sourceId);
            entity.setChunkIndex(chunk.chunkIndex());
            entity.setContent(chunk.content());
            entity.setEmbeddingFromDoubleList(vector);
            
            entities.add(entity);
        }
        
        embeddingRepository.saveAll(entities);
        LOG.info("Successfully stored {} embeddings for document {}", entities.size(), sourceId);
    }

    public List<Double> generateEmbedding(String text) {
        LOG.debug("Generating embedding for text length {}", text.length());
        try {
            return embeddingProvider.getEmbedding(text);
        } catch (Exception e) {
            LOG.error("Failed to generate embedding", e);
            throw new AIServiceException("Failed to generate embedding from provider: " + e.getMessage(), e);
        }
    }

    public List<List<Double>> generateEmbeddings(List<String> chunks) {
        LOG.info("Generating embeddings for {} chunks", chunks.size());
        List<List<Double>> embeddings = new ArrayList<>();
        for (String chunk : chunks) {
            embeddings.add(generateEmbedding(chunk));
        }
        return embeddings;
    }
}
