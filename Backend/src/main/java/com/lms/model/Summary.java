package com.lms.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "summaries")
public class Summary {

    @Id
    private String id;
    
    @Indexed(unique = true)
    private String sourceId;
    
    private String summary;
    private List<String> keyPoints;
    private List<String> importantTakeaways;
    private List<String> definitions;
    private List<String> nextSteps;
    private String generatedAt;
    private String generationSource;

    public Summary() {
        this.generatedAt = Instant.now().toString();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public List<String> getKeyPoints() { return keyPoints; }
    public void setKeyPoints(List<String> keyPoints) { this.keyPoints = keyPoints; }
    public List<String> getImportantTakeaways() { return importantTakeaways; }
    public void setImportantTakeaways(List<String> importantTakeaways) { this.importantTakeaways = importantTakeaways; }
    public List<String> getDefinitions() { return definitions; }
    public void setDefinitions(List<String> definitions) { this.definitions = definitions; }
    public List<String> getNextSteps() { return nextSteps; }
    public void setNextSteps(List<String> nextSteps) { this.nextSteps = nextSteps; }
    public String getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(String generatedAt) { this.generatedAt = generatedAt; }
    public String getGenerationSource() { return generationSource; }
    public void setGenerationSource(String generationSource) { this.generationSource = generationSource; }
}
