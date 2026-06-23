package com.lms.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.model.KnowledgeGraph;
import com.lms.model.Source;
import com.lms.repository.KnowledgeGraphRepository;
import com.lms.repository.SourceRepository;
import com.lms.service.ai.AIOrchestratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
public class KnowledgeGraphService {

    private static final Logger LOG = LoggerFactory.getLogger(KnowledgeGraphService.class);

    private final KnowledgeGraphRepository knowledgeGraphRepository;
    private final SourceRepository sourceRepository;
    private final AIOrchestratorService aiOrchestratorService;
    private final JobStatusService jobStatusService;
    private final ObjectMapper objectMapper;
    private final ObjectMapper relaxedMapper;

    @Autowired
    public KnowledgeGraphService(KnowledgeGraphRepository knowledgeGraphRepository,
                                 SourceRepository sourceRepository,
                                 AIOrchestratorService aiOrchestratorService,
                                 JobStatusService jobStatusService,
                                 ObjectMapper objectMapper) {
        this.knowledgeGraphRepository = knowledgeGraphRepository;
        this.sourceRepository = sourceRepository;
        this.aiOrchestratorService = aiOrchestratorService;
        this.jobStatusService = jobStatusService;
        this.objectMapper = objectMapper;

        this.relaxedMapper = new ObjectMapper();
        this.relaxedMapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
        this.relaxedMapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
        this.relaxedMapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, true);
    }

    @Async
    public void generateKnowledgeGraphAsync(String userId, String topic, String sourceId) {
        jobStatusService.createOrUpdateJob(sourceId, "KNOWLEDGEGRAPH", "PROCESSING", "Generating knowledge graph...");
        try {
            generateKnowledgeGraph(userId, topic, sourceId);
            jobStatusService.createOrUpdateJob(sourceId, "KNOWLEDGEGRAPH", "COMPLETED", "Knowledge graph generated successfully");
        } catch (Exception e) {
            LOG.error("Async knowledge graph generation failed for sourceId {}: {}", sourceId, e.getMessage());
            jobStatusService.createOrUpdateJob(sourceId, "KNOWLEDGEGRAPH", "FAILED", e.getMessage());
        }
    }

    public KnowledgeGraph generateKnowledgeGraph(String userId, String topic, String sourceId) {
        LOG.info("[KNOWLEDGE_GRAPH_START] REQUEST received for userId={} sourceId={}", userId, sourceId);
        
        // Return cached if exists
        Optional<KnowledgeGraph> existing = knowledgeGraphRepository.findByUserIdAndSourceId(userId, sourceId);
        if (existing.isPresent()) {
            LOG.info("Returning cached knowledge graph for userId={} sourceId={}", userId, sourceId);
            return existing.get();
        }

        String contextText = "";
        Source source = null;
        if (sourceId != null) {
            source = sourceRepository.findById(sourceId).orElse(null);
        }
        if (source != null && source.getExtractedText() != null && !source.getExtractedText().isBlank()) {
            contextText = source.getExtractedText();
            if (contextText.length() > 4000) {
                contextText = contextText.substring(0, 4000);
            }
        } else {
            contextText = topic; // Fallback to topic name
        }

        String prompt = "Analyze this educational text and extract a structured knowledge graph of key concepts (nodes) and their relationships (edges).\n\n" +
                "Text:\n" + contextText + "\n\n" +
                "Respond ONLY with a valid JSON object. Do not include any markdown styling (like ```json) or explanations. " +
                "The JSON object MUST follow this exact schema:\n" +
                "{\n" +
                "  \"nodes\": [\n" +
                "    { \"id\": \"string\", \"label\": \"string\", \"type\": \"string\" }\n" +
                "  ],\n" +
                "  \"edges\": [\n" +
                "    { \"from\": \"string\", \"to\": \"string\", \"label\": \"string\" }\n" +
                "  ]\n" +
                "}\n" +
                "Ensure that 'from' and 'to' values in edges refer to existing node 'id' values.";

        long startTime = System.currentTimeMillis();
        try {
            String aiResponseText = aiOrchestratorService.generate(prompt, Map.of("format", "json", "timeoutMs", 120000));
            long latency = System.currentTimeMillis() - startTime;

            // Strip markdown fences
            String cleanJson = aiResponseText.replaceAll("```json", "").replaceAll("```", "").trim();
            int start = cleanJson.indexOf('{');
            int end = cleanJson.lastIndexOf('}');
            if (start >= 0 && end > start) {
                cleanJson = cleanJson.substring(start, end + 1);
            }

            Map<String, Object> resultMap;
            try {
                resultMap = relaxedMapper.readValue(cleanJson, new TypeReference<Map<String, Object>>() {});
            } catch (Exception parseEx) {
                LOG.warn("Failed to parse AI knowledge graph JSON, falling back to simple structure. Error: {}", parseEx.getMessage());
                resultMap = createFallbackGraph(topic);
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> nodes = (List<Map<String, Object>>) resultMap.getOrDefault("nodes", List.of());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> edges = (List<Map<String, Object>>) resultMap.getOrDefault("edges", List.of());

            KnowledgeGraph knowledgeGraph = new KnowledgeGraph(userId, sourceId, nodes, edges);
            knowledgeGraph.setGenerationSource("AI");
            
            KnowledgeGraph saved;
            try {
                saved = knowledgeGraphRepository.save(knowledgeGraph);
                LOG.info("Saved knowledge graph successfully for sourceId={}", sourceId);
            } catch (org.springframework.dao.DuplicateKeyException dke) {
                saved = knowledgeGraphRepository.findByUserIdAndSourceId(userId, sourceId)
                        .orElseThrow(() -> new RuntimeException("KnowledgeGraph save collision", dke));
            }

            LOG.info("KNOWLEDGEGRAPH_COMPLETED: sourceId={}", sourceId);
            return saved;
        } catch (Exception e) {
            LOG.error("Failed to generate knowledge graph: {}", e.getMessage(), e);
            throw new com.lms.exception.AIServiceException("Knowledge graph generation failed", e);
        }
    }

    private Map<String, Object> createFallbackGraph(String topic) {
        String rootId = UUID.randomUUID().toString();
        Map<String, Object> rootNode = Map.of("id", rootId, "label", topic != null ? topic : "Main Topic", "type", "Topic");
        
        String c1Id = UUID.randomUUID().toString();
        Map<String, Object> c1Node = Map.of("id", c1Id, "label", "Core Concepts", "type", "Concept");
        
        String c2Id = UUID.randomUUID().toString();
        Map<String, Object> c2Node = Map.of("id", c2Id, "label", "Key Takeaways", "type", "Concept");
        
        List<Map<String, Object>> nodes = List.of(rootNode, c1Node, c2Node);
        List<Map<String, Object>> edges = List.of(
            Map.of("from", rootId, "to", c1Id, "label", "contains"),
            Map.of("from", rootId, "to", c2Id, "label", "explains")
        );
        
        Map<String, Object> graph = new HashMap<>();
        graph.put("nodes", nodes);
        graph.put("edges", edges);
        return graph;
    }

    public Optional<KnowledgeGraph> getKnowledgeGraph(String userId, String sourceId) {
        Source source = sourceRepository.findById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Source not found"));
        if (!source.getUserId().equals(userId)) {
            throw new SecurityException("Unauthorized access to source");
        }
        return knowledgeGraphRepository.findByUserIdAndSourceId(userId, sourceId);
    }
}
