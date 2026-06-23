package com.lms.service.ai.provider;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.exception.AIServiceException;
import com.lms.service.LlmProvider;

import reactor.core.publisher.Flux;

@Service("mistralProvider")
public class MistralProvider implements LlmProvider {

    private static final Logger LOG = LoggerFactory.getLogger(MistralProvider.class);

    private final WebClient client;
    private final ObjectMapper objectMapper;

    @Value("${app.ai.fast-mode:false}")
    private boolean fastMode;

    @Value("${app.ai.fast-model:phi3.5}")
    private String fastModel;

    @Value("${app.ai.production-model:mistral}")
    private String productionModel;

    @Value("${app.ai.timeout-ms:60000}")
    private long timeoutMs;

    public MistralProvider(
            ObjectMapper objectMapper,
            @Value("${app.ai.mistral.api-url:http://localhost:11434/v1/chat/completions}") String apiUrl,
            @Value("${app.ai.mistral.api-key:}") String apiKey,
            @Value("${app.ai.timeout-ms:60000}") long timeoutMs) {

        this.objectMapper = objectMapper;

        long timeoutSec = Math.max(30L, timeoutMs / 1000L);
        reactor.netty.http.client.HttpClient httpClient = reactor.netty.http.client.HttpClient.create()
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new io.netty.handler.timeout.ReadTimeoutHandler((int) timeoutSec,
                                java.util.concurrent.TimeUnit.SECONDS))
                        .addHandlerLast(new io.netty.handler.timeout.WriteTimeoutHandler((int) timeoutSec,
                                java.util.concurrent.TimeUnit.SECONDS)));

        this.client = WebClient.builder()
                .baseUrl(apiUrl)
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    public String getActiveModel() {
        return fastMode ? fastModel : productionModel;
    }

    @Override
    public String generate(String prompt, Map<String, Object> options) {
        long startTime = System.currentTimeMillis();
        String modelName = getActiveModel();
        LOG.info("MISTRAL REQUEST: model={}, prompt_length={}", modelName, prompt.length());

        LOG.info("PROMPT_LENGTH={}", prompt.length());
        LOG.info("PROMPT_PREVIEW={}", prompt.substring(0, Math.min(1500, prompt.length())));

        try {
            boolean jsonFormat = options != null && "json".equals(options.get("format"));

            Object responseFormat = jsonFormat ? Map.of("type", "json_object") : null;

            Map<String, Object> body = new java.util.HashMap<>();
            body.put("model", modelName);
            body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
            body.put("stream", false);
            body.put("temperature", 0.2);
            body.put("max_tokens", 2048);
            body.put("top_p", 0.9);
            body.put("options", Map.of(
                    "temperature", 0.2,
                    "num_predict", 2048,
                    "top_p", 0.9,
                    "repeat_penalty", 1.1));

            if (responseFormat != null) {
                body.put("response_format", responseFormat);
            }

            long currentTimeoutMs = this.timeoutMs;
            if (options != null && options.containsKey("timeoutMs")) {
                currentTimeoutMs = ((Number) options.get("timeoutMs")).longValue();
            }

            LOG.info("REQUEST SENT TO MISTRAL");
            String rawResponse = client.post()
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(currentTimeoutMs))
                    .block();

            long latency = System.currentTimeMillis() - startTime;
            LOG.info("RESPONSE RECEIVED");
            LOG.info("MISTRAL LATENCY: {}ms", latency);

            if (rawResponse == null || rawResponse.isBlank()) {
                LOG.error("MISTRAL FAILURE: Received null/empty response after {}ms", latency);
                throw new AIServiceException("Mistral returned an empty response after " + latency + "ms");
            }

            try {
                JsonNode node = objectMapper.readTree(rawResponse);
                if (node.has("choices") && node.get("choices").isArray() && node.get("choices").size() > 0) {
                    JsonNode messageNode = node.get("choices").get(0).get("message");
                    if (messageNode != null && messageNode.has("content")) {
                        String text = messageNode.get("content").asText();
                        LOG.info("RESPONSE PARSED");
                        LOG.info("MISTRAL SUCCESS: latency={}ms, response_length={}", latency, text.length());
                        return text.trim();
                    }
                }
                LOG.warn("MISTRAL: Response missing choices[0].message.content. Returning raw text.");
                return rawResponse.trim();
            } catch (Exception parseEx) {
                LOG.warn("MISTRAL: Response is not valid JSON from API: {}", parseEx.getMessage());
                return rawResponse.trim();
            }

        } catch (WebClientResponseException e) {
            long latency = System.currentTimeMillis() - startTime;
            LOG.error("MISTRAL HTTP ERROR: status={}, body={}, latency={}ms",
                    e.getStatusCode(), e.getResponseBodyAsString(), latency, e);
            throw new AIServiceException("Mistral HTTP " + e.getStatusCode() + ": " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            LOG.error("MISTRAL FAILURE: latency={}ms, message={}", latency, e.getMessage(), e);
            throw new AIServiceException("Mistral request failed after " + latency + "ms: " + e.getMessage(), e);
        }
    }

    @Override
    public Flux<String> generateStream(String prompt, Map<String, Object> options) {
        long startTime = System.currentTimeMillis();
        String modelName = getActiveModel();
        LOG.info("MISTRAL STREAM REQUEST: model={}, prompt_length={}", modelName, prompt.length());

        LOG.info("PROMPT_LENGTH={}", prompt.length());
        LOG.info("PROMPT_PREVIEW={}", prompt.substring(0, Math.min(1500, prompt.length())));

        try {
            boolean jsonFormat = options != null && "json".equals(options.get("format"));
            Object responseFormat = jsonFormat ? Map.of("type", "json_object") : null;

            Map<String, Object> body = new java.util.HashMap<>();
            body.put("model", modelName);
            body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
            body.put("stream", true);
            body.put("temperature", 0.2);
            body.put("max_tokens", 2048);
            body.put("top_p", 0.9);
            body.put("options", Map.of(
                    "temperature", 0.2,
                    "num_predict", 2048,
                    "top_p", 0.9,
                    "repeat_penalty", 1.1));

            if (responseFormat != null) {
                body.put("response_format", responseFormat);
            }

            long currentTimeoutMs = this.timeoutMs;
            if (options != null && options.containsKey("timeoutMs")) {
                currentTimeoutMs = ((Number) options.get("timeoutMs")).longValue();
            }

            return client.post()
                    .bodyValue(body)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .timeout(Duration.ofMillis(currentTimeoutMs))
                    .filter(line -> line != null && !line.trim().isEmpty())
                    .map(line -> {
                        // Mistral returns SSE format: data: {...}
                        if (line.startsWith("data: ")) {
                            String json = line.substring(6).trim();
                            if ("[DONE]".equals(json)) {
                                return "";
                            }
                            try {
                                JsonNode node = objectMapper.readTree(json);
                                if (node.has("choices") && node.get("choices").isArray()
                                        && node.get("choices").size() > 0) {
                                    JsonNode deltaNode = node.get("choices").get(0).get("delta");
                                    if (deltaNode != null && deltaNode.has("content")) {
                                        return deltaNode.get("content").asText();
                                    }
                                }
                            } catch (Exception e) {
                                LOG.warn("Dropped malformed Mistral stream chunk: {}", json);
                            }
                        }
                        return "";
                    })
                    .filter(text -> !text.isEmpty())
                    .doOnComplete(() -> LOG.info("MISTRAL STREAM COMPLETED"))
                    .onErrorResume(e -> Flux.error(
                            new AIServiceException("Mistral stream failed: " + e.getMessage(), e)));
        } catch (Exception e) {
            LOG.error("MISTRAL STREAM INITIALIZATION FAILURE", e);
            return Flux.error(new AIServiceException("Mistral stream init failed: " + e.getMessage(), e));
        }
    }
}
