package com.lms.service.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service("mistralEmbeddingProvider")
public class MistralEmbeddingProvider implements EmbeddingProvider {

    private static final Logger LOG = LoggerFactory.getLogger(MistralEmbeddingProvider.class);

    private final WebClient client;
    private final ObjectMapper objectMapper;

    @Value("${app.ai.timeout-ms:60000}")
    private long timeoutMs;

    @Value("${app.ai.mistral.model:mistral}")
    private String activeModel;

    public MistralEmbeddingProvider(
            ObjectMapper objectMapper,
            @Value("${app.ai.mistral.api-url:http://localhost:11434/v1/chat/completions}") String apiUrl,
            @Value("${app.ai.mistral.api-key:}") String apiKey) {
        
        this.objectMapper = objectMapper;
        // Strip out the chat completions part to get the base API for embeddings
        String baseApiUrl = apiUrl.replace("/chat/completions", "/embeddings");
        if (!baseApiUrl.endsWith("/embeddings")) {
             // In case apiUrl was completely custom, ensure we have the correct path
             if (baseApiUrl.endsWith("/v1")) {
                 baseApiUrl = baseApiUrl + "/embeddings";
             } else {
                 if (baseApiUrl.endsWith("/")) {
                     baseApiUrl = baseApiUrl + "v1/embeddings";
                 } else {
                     baseApiUrl = baseApiUrl + "/v1/embeddings";
                 }
             }
        }
        
        this.client = WebClient.builder()
                .baseUrl(baseApiUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public List<Double> getEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return generateFallbackEmbedding("");
        }

        try {
            Map<String, Object> body = Map.of(
                    "model", activeModel,
                    "input", List.of(text)
            );

            String rawResponse = client.post()
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .block();

            if (rawResponse != null) {
                JsonNode node = objectMapper.readTree(rawResponse);
                if (node.has("data") && node.get("data").isArray() && node.get("data").size() > 0) {
                    JsonNode dataNode = node.get("data").get(0);
                    if (dataNode.has("embedding") && dataNode.get("embedding").isArray()) {
                        return convertToListOfDouble(dataNode.get("embedding"));
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("Mistral embedding failed. Generating deterministic fallback. Error: {}", e.getMessage());
        }

        return generateFallbackEmbedding(text);
    }

    private List<Double> convertToListOfDouble(JsonNode embeddingArray) {
        List<Double> doubles = new ArrayList<>();
        for (JsonNode item : embeddingArray) {
            doubles.add(item.asDouble());
        }
        return doubles;
    }

    private List<Double> generateFallbackEmbedding(String text) {
        List<Double> fallback = new ArrayList<>();
        int dimensions = 1024; // Mistral-embed size
        int hash = text.hashCode();
        for (int i = 0; i < dimensions; i++) {
            double val = Math.sin(hash + i) * 0.5;
            fallback.add(val);
        }
        return fallback;
    }
}
