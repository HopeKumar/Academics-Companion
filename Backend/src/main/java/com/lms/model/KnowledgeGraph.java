package com.lms.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Document(collection = "knowledge_graphs")
@org.springframework.data.mongodb.core.index.CompoundIndex(def = "{'userId': 1, 'sourceId': 1}", unique = true)
public class KnowledgeGraph {

    @Id
    private String id;
    
    @Indexed
    private String sourceId;
    
    @Indexed
    private String userId;
    
    private List<Map<String, Object>> nodes;
    private List<Map<String, Object>> edges;
    
    @Indexed
    private String generatedAt;
    private String generationSource;

    public KnowledgeGraph() {
        this.generatedAt = Instant.now().toString();
    }

    public KnowledgeGraph(String userId, String sourceId, List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {
        this();
        this.userId = userId;
        this.sourceId = sourceId;
        this.nodes = nodes;
        this.edges = edges;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public List<Map<String, Object>> getNodes() {
        return nodes;
    }

    public void setNodes(List<Map<String, Object>> nodes) {
        this.nodes = nodes;
    }

    public List<Map<String, Object>> getEdges() {
        return edges;
    }

    public void setEdges(List<Map<String, Object>> edges) {
        this.edges = edges;
    }

    public String getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(String generatedAt) {
        this.generatedAt = generatedAt;
    }

    public String getGenerationSource() {
        return generationSource;
    }

    public void setGenerationSource(String generationSource) {
        this.generationSource = generationSource;
    }
}
