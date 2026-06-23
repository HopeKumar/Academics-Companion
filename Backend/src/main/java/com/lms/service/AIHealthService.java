package com.lms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AIHealthService {
    
    private static final Logger LOG = LoggerFactory.getLogger(AIHealthService.class);
    
    private final WebClient client;
    private final ObjectMapper objectMapper;
    private volatile Map<String, Object> lastHealthCheck = Map.of("status", "UNKNOWN");
    private final String expectedModel;
    private final String baseApiUrl;

    public AIHealthService(
            ObjectMapper objectMapper,
            @Value("${app.ai.mistral.api-url:http://localhost:11434/v1/chat/completions}") String apiUrl,
            @Value("${app.ai.mistral.api-key:}") String apiKey,
            @Value("${app.ai.mistral.model:mistral}") String expectedModel) {
        
        this.objectMapper = objectMapper;
        this.expectedModel = expectedModel;
        
        String tempBaseUrl;
        if (apiUrl.contains("localhost") || apiUrl.contains("127.0.0.1") || apiUrl.contains("11434")) {
            int portIndex = apiUrl.indexOf(":", 7);
            if (portIndex != -1) {
                int slashIndex = apiUrl.indexOf("/", portIndex);
                if (slashIndex != -1) {
                    tempBaseUrl = apiUrl.substring(0, slashIndex + 1);
                } else {
                    tempBaseUrl = apiUrl;
                }
            } else {
                tempBaseUrl = "http://localhost:11434/";
            }
        } else {
            tempBaseUrl = apiUrl.replace("/chat/completions", "");
            if (!tempBaseUrl.endsWith("/")) {
                tempBaseUrl = tempBaseUrl + "/";
            }
        }
        this.baseApiUrl = tempBaseUrl;
        
        this.client = WebClient.builder()
                .baseUrl(this.baseApiUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
        checkAIHealth(); // Initial check on startup
    }

    @Scheduled(fixedRate = 60000)
    public void checkAIHealth() {
        Map<String, Object> result = new HashMap<>();
        long startTime = System.currentTimeMillis();
        try {
            String checkPath = "models";
            if (this.baseApiUrl.contains("11434")) {
                checkPath = "api/tags";
            }
            
            String modelsResponse = client.get()
                    .uri(checkPath)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();
            
            long totalLatency = System.currentTimeMillis() - startTime;

            result.put("status", "UP");
            result.put("provider", "Ollama");
            result.put("latencyMs", totalLatency);

            boolean hasPrimaryModel = false;
            List<String> availableModels = new ArrayList<>();

            if (modelsResponse != null && !modelsResponse.isBlank()) {
                try {
                    JsonNode root = objectMapper.readTree(modelsResponse);
                    if (root.has("data") && root.get("data").isArray()) {
                        for (JsonNode modelNode : root.get("data")) {
                            if (modelNode.has("id")) {
                                String modelId = modelNode.get("id").asText();
                                availableModels.add(modelId);
                                if (expectedModel.equals(modelId) || modelId.contains("mistral") || modelId.contains(expectedModel)) {
                                    hasPrimaryModel = true;
                                }
                            }
                        }
                    } else if (root.has("models") && root.get("models").isArray()) {
                        for (JsonNode modelNode : root.get("models")) {
                            if (modelNode.has("name")) {
                                String modelName = modelNode.get("name").asText();
                                availableModels.add(modelName);
                                if (expectedModel.equals(modelName) || modelName.contains("mistral") || modelName.contains(expectedModel)) {
                                    hasPrimaryModel = true;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    LOG.warn("Failed to parse models response: {}", e.getMessage());
                }
            }

            result.put("models", availableModels);
            result.put("primaryModelAvailable", hasPrimaryModel);
            result.put("model", expectedModel);

            this.lastHealthCheck = result;

        } catch (Exception e) {
            long failLatency = System.currentTimeMillis() - startTime;
            LOG.warn("AI Health Check failed after {}ms: {}", failLatency, e.getMessage());
            result.put("status", "DOWN");
            result.put("reason", e.getMessage());
            result.put("latencyMs", failLatency);
            this.lastHealthCheck = result;
        }
    }

    public String getHealthStatus() {
        Object status = lastHealthCheck.get("status");
        return status != null ? status.toString() : "UNKNOWN";
    }

    public Map<String, Object> getDetailedHealth() {
        return lastHealthCheck;
    }
}
