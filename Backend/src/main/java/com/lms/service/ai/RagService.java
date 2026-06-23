package com.lms.service.ai;

import com.lms.dto.ai.ChatResponse;
import com.lms.model.EmbeddingEntity;
import com.lms.repository.EmbeddingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RagService {

    private static final Logger LOG = LoggerFactory.getLogger(RagService.class);

    private final EmbeddingService embeddingService;
    private final AIOrchestratorService aiOrchestratorService;
    private final EmbeddingRepository embeddingRepository;

    public RagService(EmbeddingService embeddingService, AIOrchestratorService aiOrchestratorService, EmbeddingRepository embeddingRepository) {
        this.embeddingService = embeddingService;
        this.aiOrchestratorService = aiOrchestratorService;
        this.embeddingRepository = embeddingRepository;
    }

    public List<EmbeddingEntity> retrieveRelevantChunks(String sourceId, String question, int limit) {
        LOG.info("Retrieving top {} relevant chunks for document {}", limit, sourceId);
        
        // 1. Generate embedding for the question
        List<Double> questionEmbedding = embeddingService.generateEmbedding(question);
        
        // 2. Query the database and sort by cosine similarity in memory
        List<EmbeddingEntity> allChunks = embeddingRepository.findBySourceId(sourceId);
        
        return allChunks.stream()
                .sorted((e1, e2) -> Double.compare(
                        cosineSimilarity(questionEmbedding, e2.getEmbedding()), 
                        cosineSimilarity(questionEmbedding, e1.getEmbedding())))
                .limit(limit)
                .collect(Collectors.toList());
    }

    private double cosineSimilarity(List<Double> vectorA, List<Double> vectorB) {
        if (vectorA == null || vectorB == null || vectorA.size() != vectorB.size() || vectorA.isEmpty()) {
            return 0.0;
        }
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < vectorA.size(); i++) {
            dotProduct += vectorA.get(i) * vectorB.get(i);
            normA += vectorA.get(i) * vectorA.get(i);
            normB += vectorB.get(i) * vectorB.get(i);
        }
        if (normA == 0.0 || normB == 0.0) return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    public ChatResponse answerQuestion(String sourceId, String question) {
        LOG.info("Answering question for document {}", sourceId);
        
        // Retrieve top 5 relevant chunks
        List<EmbeddingEntity> relevantChunks = retrieveRelevantChunks(sourceId, question, 5);
        
        if (relevantChunks.isEmpty()) {
            return new ChatResponse("I could not find this information in the uploaded material.", 0.0, 0, "No chunks found");
        }

        // Build context
        StringBuilder contextBuilder = new StringBuilder();
        double totalScore = 0.0;
        int count = 0;
        for (EmbeddingEntity chunk : relevantChunks) {
            contextBuilder.append("Chunk:\n").append(chunk.getContent()).append("\n\n");
            totalScore += 1.0; 
            count++;
        }
        
        double avgScore = count > 0 ? totalScore / count : 0.0;

        // Ensure AIOrchestrator respects the context
        String systemPrompt = "Answer the question using ONLY the provided context. If the answer is not in the context, reply exactly with: 'I could not find this information in the uploaded material.'\n\nContext:\n" + contextBuilder.toString();

        // Send to AIOrchestratorService
        ChatResponse baseResponse = aiOrchestratorService.chat(question, systemPrompt);
        
        String finalReply = baseResponse.reply();
        if (finalReply.contains("I could not find this information in the uploaded material")) {
            finalReply = "I could not find this information in the uploaded material.";
            avgScore = 0.0;
        }

        return new ChatResponse(finalReply, avgScore, count, "Retrieved top " + count + " chunks using bge-m3");
    }
}
