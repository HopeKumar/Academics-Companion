package com.lms.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.dto.ai.MindMapResponse;
import com.lms.model.AIResult;
import com.lms.model.DocumentChunk;
import com.lms.model.MindMap;
import com.lms.model.Source;
import com.lms.repository.MindmapRepository;
import com.lms.repository.SourceRepository;
import com.lms.service.ai.AIOrchestratorService;
import com.lms.service.ai.provider.PromptManager;

@Service
public class MindmapService {

    private static final Logger LOG = LoggerFactory.getLogger(MindmapService.class);

    private final HybridRetrievalService retrievalService;
    private final ContextBuilder contextBuilder;
    private final AIOrchestratorService aiOrchestratorService;
    private final ObjectMapper objectMapper;
    private final ObjectMapper relaxedMapper;
    private final MindmapRepository mindmapRepository;
    private final JobStatusService jobStatusService;
    private final PromptManager promptManager;
    private final SourceRepository sourceRepository;

    public MindmapService(HybridRetrievalService retrievalService, ContextBuilder contextBuilder,
                          AIOrchestratorService aiOrchestratorService, ObjectMapper objectMapper,
                          MindmapRepository mindmapRepository, JobStatusService jobStatusService,
                          PromptManager promptManager, SourceRepository sourceRepository) {
        this.retrievalService = retrievalService;
        this.contextBuilder = contextBuilder;
        this.aiOrchestratorService = aiOrchestratorService;
        this.objectMapper = objectMapper;
        this.mindmapRepository = mindmapRepository;
        this.jobStatusService = jobStatusService;
        this.promptManager = promptManager;
        this.sourceRepository = sourceRepository;
        
        this.relaxedMapper = new ObjectMapper();
        this.relaxedMapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
        this.relaxedMapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
        this.relaxedMapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, true);
    }

    @Async
    public void generateHierarchicalMindmapAsync(String userId, String topic, String sourceId) {
        jobStatusService.createOrUpdateJob(sourceId, "MINDMAP", "PROCESSING", "Generating mindmap...");
        try {
            generateHierarchicalMindmap(userId, topic, sourceId);
            jobStatusService.createOrUpdateJob(sourceId, "MINDMAP", "COMPLETED", "Mindmap generated successfully");
        } catch (Exception e) {
            LOG.error("Async mindmap generation failed for sourceId {}: {}", sourceId, e.getMessage());
            jobStatusService.createOrUpdateJob(sourceId, "MINDMAP", "FAILED", e.getMessage());
        }
    }

    //@Retry(name = "aiService", fallbackMethod = "generateHierarchicalMindmapFallback")
    public MindMap generateHierarchicalMindmap(String userId, String topic, String sourceId) {
        LOG.info("[MINDMAP_START] REQUEST received for userId={} sourceId={}", userId, sourceId);
        // Return cached if exists
        Optional<MindMap> existing = mindmapRepository.findByUserIdAndSourceId(userId, sourceId);
        if (existing.isPresent()) {
            LOG.info("Returning cached mindmap for userId={} sourceId={}", userId, sourceId);
            return existing.get();
        }

        Source source = null;
        if (sourceId != null) {
            source = sourceRepository.findById(sourceId).orElse(null);
        }

        String contextText = "";
        if (source != null && source.getExtractedText() != null && !source.getExtractedText().isBlank()) {
            String context = source.getExtractedText();
            if (context.length() > 1500) {
                context = context.substring(0, 1500);
            }
            LOG.info("TRUNCATED_CONTEXT_LENGTH={}", context.length());
            contextText = context;
            LOG.info("MINDMAP: Using source.getExtractedText() for sourceId: {}", sourceId);
        } else {
            List<DocumentChunk> chunks = retrievalService.retrieve(userId, topic, sourceId != null ? List.of(sourceId) : List.of(), 6);
            ContextBuilder.RagContext ragContext = contextBuilder.buildContext(chunks);
            contextText = ragContext.getContextText();
            LOG.info("MINDMAP: Using retrieved chunks for sourceId: {}", sourceId);
        }

        LOG.info("SOURCE_ID={}", sourceId);
        LOG.info("CONTEXT_LENGTH={}", contextText.length());
        LOG.info("CONTEXT_PREVIEW={}", contextText.substring(0, Math.min(1000, contextText.length())));

        String fullPrompt = promptManager.getMindmapPrompt() + "\n\nText:\n" + contextText;
        String preview = fullPrompt.length() > 500 ? fullPrompt.substring(0, 500) + "..." : fullPrompt;
        LOG.info("[MINDMAP_PROMPT_PREVIEW] promptPreview: {}", preview);

        long startTime = System.currentTimeMillis();
        try {
            MindMapResponse response = aiOrchestratorService.generateMindMap(contextText);
            long latency = System.currentTimeMillis() - startTime;

            String aiResponseJson = response != null ? response.mindMapJson() : "";
            LOG.info("[MINDMAP_AI_RESPONSE] rawJson: {}", aiResponseJson);

            Map<String, Object> resultMap = parseAndValidateMindMap(aiResponseJson, topic);

            MindMap mindMapDoc = new MindMap(userId, sourceId, resultMap);
            mindMapDoc.setGenerationSource("AI");

            MindMap saved;
            try {
                saved = mindmapRepository.save(mindMapDoc);
                LOG.info("[MINDMAP_SAVE_SUCCESS] Saved mindmap successfully for sourceId={}", sourceId);
            } catch (org.springframework.dao.DuplicateKeyException dke) {
                LOG.warn("DuplicateKeyException caught on saving mindmap for userId={} sourceId={}. Fetching existing.", userId, sourceId);
                saved = mindmapRepository.findByUserIdAndSourceId(userId, sourceId)
                        .orElseThrow(() -> new RuntimeException("Mindmap save collision occurred but existing mindmap could not be found", dke));
            }

            LOG.info("MINDMAP_COMPLETED: sourceId={}", sourceId);
            LOG.info("MINDMAP SOURCE = AI");
            LOG.info("[MINDMAP_COMPLETE] Generated mindmap in {}ms: root='{}', children={}", latency, 
                    resultMap.get("root"), 
                    resultMap.containsKey("children") ? ((List<?>) resultMap.get("children")).size() : 0);
            return saved;

        } catch (Exception e) {
            LOG.error("Failed to generate hierarchical mindmap: {}", e.getMessage(), e);
            throw new com.lms.exception.AIServiceException("Hierarchical mindmap generation failed", e);
        }
    }

    public MindMap generateHierarchicalMindmapFallback(String userId, String topic, String sourceId, Throwable t) {
        LOG.error("HIERARCHICAL MINDMAP FALLBACK TRIGGERED. Root cause class={}, message={}",
                t.getClass().getSimpleName(), t.getMessage(), t);
        throw new com.lms.exception.AIServiceException("Hierarchical mindmap generation failed: " + t.getMessage(), t);
    }
    
    private Map<String, Object> createFallbackStructure(String fallbackTopic) {
        String rootVal = fallbackTopic != null ? fallbackTopic : "Document Overview";
        return Map.of(
            "id", UUID.randomUUID().toString(),
            "root", rootVal,
            "label", rootVal,
            "children", List.of(
                Map.of("id", UUID.randomUUID().toString(), "label", "Key Concepts", "children", List.of()),
                Map.of("id", UUID.randomUUID().toString(), "label", "Main Ideas", "children", List.of()),
                Map.of("id", UUID.randomUUID().toString(), "label", "Details", "children", List.of())
            )
        );
    }

    /**
     * Parses the raw JSON from the AI and validates it has the expected structure.
     * Handles various response formats gracefully.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseAndValidateMindMap(String rawJson, String fallbackTopic) {
        if (rawJson == null || rawJson.isBlank()) {
            LOG.error("Empty mindmap JSON");
            throw new RuntimeException("Empty mindmap JSON returned by AI");
        }

        try {
            String cleaned = rawJson.trim();

            // Strip markdown code fences if present
            if (cleaned.startsWith("```")) {
                int firstNewline = cleaned.indexOf('\n');
                if (firstNewline > 0) cleaned = cleaned.substring(firstNewline + 1);
                if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.length() - 3);
                cleaned = cleaned.trim();
            }

            if (!cleaned.endsWith("}") && !cleaned.endsWith("]")) {
                throw new com.lms.exception.AIServiceException("Incomplete JSON response from AI");
            }

            // Extract JSON object
            int start = cleaned.indexOf('{');
            int end = cleaned.lastIndexOf('}');
            if (start >= 0 && end > start) {
                cleaned = cleaned.substring(start, end + 1);
            }

            Map<String, Object> parsed = relaxedMapper.readValue(cleaned, new TypeReference<>() {});

            // Auto-unwrap single-key wrapper maps if the inner map contains mindmap fields
            if (parsed.size() == 1) {
                Object inner = parsed.values().iterator().next();
                if (inner instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> innerMap = (Map<String, Object>) inner;
                    if (innerMap.containsKey("root") || innerMap.containsKey("children") || innerMap.containsKey("label") || innerMap.containsKey("topic") || innerMap.containsKey("title")) {
                        parsed = innerMap;
                    }
                }
            }

            Map<String, Object> normalizedRoot = normalizeNode(parsed, 0, fallbackTopic != null ? fallbackTopic : "Document Overview");
            if (normalizedRoot == null) {
                throw new RuntimeException("Mindmap normalized to null");
            }
            return normalizedRoot;

        } catch (Exception e) {
            LOG.error("Failed to parse mindmap JSON: '{}' — error: {}", 
                    rawJson.length() > 200 ? rawJson.substring(0, 200) + "..." : rawJson, 
                    e.getMessage());
            throw new RuntimeException("Failed to parse mindmap JSON: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeNode(Object rawNode, int level, String fallbackTitle) {
        if (rawNode == null) return null;
        
        Map<String, Object> nodeMap;
        if (rawNode instanceof Map) {
            nodeMap = new HashMap<>((Map<String, Object>) rawNode);
        } else if (rawNode instanceof String) {
            nodeMap = new HashMap<>();
            nodeMap.put("title", rawNode);
        } else {
            nodeMap = new HashMap<>();
            nodeMap.put("title", fallbackTitle);
        }
        
        // Ensure ID
        String id = nodeMap.containsKey("id") && nodeMap.get("id") != null 
            ? String.valueOf(nodeMap.get("id")) 
            : UUID.randomUUID().toString();
        nodeMap.put("id", id);
        
        // Ensure Title / Label
        String title = null;
        if (nodeMap.containsKey("title") && nodeMap.get("title") != null) {
            title = String.valueOf(nodeMap.get("title"));
        } else if (nodeMap.containsKey("label") && nodeMap.get("label") != null) {
            title = String.valueOf(nodeMap.get("label"));
        } else if (nodeMap.containsKey("root") && nodeMap.get("root") != null) {
            title = String.valueOf(nodeMap.get("root"));
        } else if (nodeMap.containsKey("name") && nodeMap.get("name") != null) {
            title = String.valueOf(nodeMap.get("name"));
        } else if (nodeMap.containsKey("topic") && nodeMap.get("topic") != null) {
            title = String.valueOf(nodeMap.get("topic"));
        } else if (nodeMap.containsKey("concept") && nodeMap.get("concept") != null) {
            title = String.valueOf(nodeMap.get("concept"));
        }
        
        if (title == null || title.isBlank() || "Unnamed".equalsIgnoreCase(title) || "Node".equalsIgnoreCase(title) || "null".equalsIgnoreCase(title)) {
            title = fallbackTitle;
        }
        nodeMap.put("title", title);
        nodeMap.put("label", title); // Keep label for backward compatibility
        
        // Ensure Description
        String description = nodeMap.containsKey("description") && nodeMap.get("description") != null
            ? String.valueOf(nodeMap.get("description"))
            : "";
        if (description.isBlank()) {
            description = "Explore key educational details and concepts about " + title + ".";
        }
        nodeMap.put("description", description);
        
        // Ensure Importance
        int importanceVal = 10;
        if (level == 1) importanceVal = 8;
        else if (level == 2) importanceVal = 6;
        else if (level >= 3) importanceVal = 4;
        
        if (nodeMap.containsKey("importance") && nodeMap.get("importance") != null) {
            try {
                String impStr = String.valueOf(nodeMap.get("importance"));
                impStr = impStr.replaceAll("[^0-9]", "");
                if (!impStr.isBlank()) {
                    importanceVal = Integer.parseInt(impStr);
                }
            } catch (Exception e) {
                // Keep default
            }
        }
        nodeMap.put("importance", importanceVal);
        
        // Ensure Key Points
        List<String> keyPoints = new ArrayList<>();
        if (nodeMap.containsKey("keyPoints") && nodeMap.get("keyPoints") instanceof List) {
            List<?> rawPoints = (List<?>) nodeMap.get("keyPoints");
            for (Object p : rawPoints) {
                if (p != null) keyPoints.add(String.valueOf(p));
            }
        }
        if (keyPoints.isEmpty()) {
            keyPoints.add("Key structural idea and academic context for " + title + ".");
            keyPoints.add("Core concept detail aligned with study curriculum.");
        }
        nodeMap.put("keyPoints", keyPoints);
        
        // Ensure Related Concepts
        List<String> related = new ArrayList<>();
        if (nodeMap.containsKey("related") && nodeMap.get("related") instanceof List) {
            List<?> rawRelated = (List<?>) nodeMap.get("related");
            for (Object r : rawRelated) {
                if (r != null) related.add(String.valueOf(r));
            }
        }
        if (related.isEmpty()) {
            related.add("Academic Context");
            related.add("Detailed Study Guide");
        }
        nodeMap.put("related", related);
        
        // Ensure Children
        List<Map<String, Object>> normalizedChildren = new ArrayList<>();
        Object childrenObj = nodeMap.getOrDefault("children",
            nodeMap.getOrDefault("nodes",
                nodeMap.getOrDefault("subtopics",
                    nodeMap.getOrDefault("branches", null))));
        
        if (childrenObj instanceof List) {
            List<?> rawList = (List<?>) childrenObj;
            for (Object item : rawList) {
                Map<String, Object> normChild = normalizeNode(item, level + 1, "Subconcept");
                if (normChild != null) {
                    normalizedChildren.add(normChild);
                }
            }
        }
        nodeMap.put("children", normalizedChildren);
        
        return nodeMap;
    }

    
    // Kept for backward compatibility if called, but simplified
    public AIResult<Map<String, Object>> generateMindmap(String userId, String topic, String sourceId) {
        return AIResult.failure("Mermaid mindmaps deprecated in favor of JSON hierarchical mindmaps.", "Mistral", "mistral", 0);
    }
}
