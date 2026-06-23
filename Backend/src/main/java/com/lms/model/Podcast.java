package com.lms.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Document(collection = "podcasts")
public class Podcast {

    @Id
    private String id;
    
    @Indexed
    private String userId;
    
    private String topic;
    
    @Indexed
    private String sourceId;
    
    private List<Map<String, String>> script;
    
    private String audioFile;
    private String audioUrl;
    private PodcastJobStatus audioStatus;
    
    private PodcastJobStatus status;
    
    @Indexed
    private String createdAt;
    private String generationSource;

    public Podcast() {
        this.createdAt = Instant.now().toString();
        this.status = PodcastJobStatus.PROCESSING;
        this.audioStatus = PodcastJobStatus.PENDING;
    }

    public Podcast(String userId, String topic, String sourceId, List<Map<String, String>> script) {
        this();
        this.userId = userId;
        this.topic = topic;
        this.sourceId = sourceId;
        this.script = script;
        this.status = PodcastJobStatus.COMPLETED;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }
    public List<Map<String, String>> getScript() { return script; }
    public void setScript(List<Map<String, String>> script) { this.script = script; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public PodcastJobStatus getStatus() { return status; }
    public void setStatus(PodcastJobStatus status) { this.status = status; }
    public String getAudioFile() { return audioFile; }
    public void setAudioFile(String audioFile) { this.audioFile = audioFile; }
    public String getAudioUrl() { return audioUrl; }
    public void setAudioUrl(String audioUrl) { this.audioUrl = audioUrl; }
    public PodcastJobStatus getAudioStatus() { return audioStatus; }
    public void setAudioStatus(PodcastJobStatus audioStatus) { this.audioStatus = audioStatus; }
    public String getGenerationSource() { return generationSource; }
    public void setGenerationSource(String generationSource) { this.generationSource = generationSource; }
}
