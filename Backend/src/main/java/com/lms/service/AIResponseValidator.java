package com.lms.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class AIResponseValidator {
    
    private static final Logger LOG = LoggerFactory.getLogger(AIResponseValidator.class);
    private final ObjectMapper objectMapper;

    public AIResponseValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void logAIRequest(String userId, String endpoint, String model, long latency, String rawResponse, boolean success) {
        MDC.put("userId", userId != null ? userId : "anonymous");
        MDC.put("endpoint", endpoint);
        MDC.put("model", model != null ? model : "unknown");
        
        // Approximate token counting based on char length (roughly 4 chars per token)
        int estimatedTokens = rawResponse != null ? rawResponse.length() / 4 : 0;
        
        LOG.info("AI Generation [{}] - Success: {}, Latency: {}ms, Tokens: ~{}", endpoint, success, latency, estimatedTokens);
        LOG.debug("Raw AI Response:\n{}", rawResponse);
        
        MDC.remove("userId");
        MDC.remove("endpoint");
        MDC.remove("model");
    }

    public boolean isDegradedFallback(String aiResponse) {
        if (aiResponse == null || aiResponse.isBlank()) return true;
        try {
            JsonNode node = objectMapper.readTree(aiResponse);
            return node.has("status") && "degraded".equals(node.get("status").asText());
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    public String extractJson(String rawResponse) {
        if (rawResponse == null) return "";
        String cleanJson = rawResponse;
        int firstBrace = cleanJson.indexOf('{');
        int lastBrace = cleanJson.lastIndexOf('}');
        int firstBracket = cleanJson.indexOf('[');
        int lastBracket = cleanJson.lastIndexOf(']');

        // Extract array [] if it comes first or if it's the only one
        if (firstBracket != -1 && lastBracket != -1 && lastBracket >= firstBracket &&
            (firstBrace == -1 || firstBracket < firstBrace || (firstBrace > firstBracket && lastBrace < lastBracket))) {
            return cleanJson.substring(firstBracket, lastBracket + 1).trim();
        }

        // Extract object {}
        if (firstBrace != -1 && lastBrace != -1 && lastBrace >= firstBrace) {
            return cleanJson.substring(firstBrace, lastBrace + 1).trim();
        }

        return cleanJson.trim();
    }

    public String repairJson(String json) {
        if (json == null) return "";
        // 1. Remove trailing commas before closing braces/brackets
        String repaired = json.replaceAll(",\\s*}", "}").replaceAll(",\\s*]", "]");
        
        // 2. Very naive unescaped quote fix (only if desperately failing).
        // Since we are doing a 1-pass repair, fixing internal unescaped quotes is tricky with regex.
        // We will rely on Jackson's ALLOW_UNQUOTED_CONTROL_CHARS and ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER
        // which can be enabled on the ObjectMapper if needed. For now, the trailing comma fix is the most common issue.
        return repaired;
    }
}
