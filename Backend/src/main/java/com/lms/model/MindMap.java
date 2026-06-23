package com.lms.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document(collection = "mindmaps")
@org.springframework.data.mongodb.core.index.CompoundIndex(def = "{'userId': 1, 'sourceId': 1}", unique = true)
public class MindMap {

    @Id
    private String id;
    
    @Indexed
    private String sourceId;
    
    @Indexed
    private String userId;
    
    private Map<String, Object> mindMap;
    
    @Indexed
    private String generatedAt;
    private String generationSource;

    public MindMap() {
        this.generatedAt = Instant.now().toString();
    }

    public MindMap(String userId, String sourceId, Map<String, Object> mindMap) {
        this();
        this.userId = userId;
        this.sourceId = sourceId;
        this.mindMap = mindMap;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public Map<String, Object> getMindMap() { return mindMap; }
    public void setMindMap(Map<String, Object> mindMap) { this.mindMap = mindMap; }
    public String getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(String generatedAt) { this.generatedAt = generatedAt; }
    public String getGenerationSource() { return generationSource; }
    public void setGenerationSource(String generationSource) { this.generationSource = generationSource; }
}
