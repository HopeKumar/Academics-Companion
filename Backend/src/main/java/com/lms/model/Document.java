package com.lms.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

@org.springframework.data.mongodb.core.mapping.Document(collection = "documents")
public class Document {
    @Id
    private String id;

    @Field("owner_id")
    private String ownerId;

    @Field("original_filename")
    private String originalFilename;

    @Field("file_hash_sha256")
    private String fileHashSha256;

    @Field("size_bytes")
    private Long sizeBytes;

    @Field("mime_type")
    private String mimeType;

    private DocumentStatus status = DocumentStatus.UPLOADING;

    @Field("summary_status")
    private ProcessingStatus summaryStatus = ProcessingStatus.PENDING;

    @Field("quiz_status")
    private ProcessingStatus quizStatus = ProcessingStatus.PENDING;

    @Field("flashcard_status")
    private ProcessingStatus flashcardStatus = ProcessingStatus.PENDING;

    @Field("mindmap_status")
    private ProcessingStatus mindmapStatus = ProcessingStatus.PENDING;

    @Field("discussion_status")
    private ProcessingStatus discussionStatus = ProcessingStatus.PENDING;

    @Field("embedding_status")
    private ProcessingStatus embeddingStatus = ProcessingStatus.PENDING;

    @Field("podcast_status")
    private ProcessingStatus podcastStatus = ProcessingStatus.PENDING;

    @Field("created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Field("updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    private List<DocumentArtifact> artifacts = new ArrayList<>();

    // Getters and Setters

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }

    public String getFileHashSha256() { return fileHashSha256; }
    public void setFileHashSha256(String fileHashSha256) { this.fileHashSha256 = fileHashSha256; }

    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public DocumentStatus getStatus() { return status; }
    public void setStatus(DocumentStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<DocumentArtifact> getArtifacts() { return artifacts; }
    public void setArtifacts(List<DocumentArtifact> artifacts) { this.artifacts = artifacts; }

    public ProcessingStatus getSummaryStatus() { return summaryStatus; }
    public void setSummaryStatus(ProcessingStatus summaryStatus) { this.summaryStatus = summaryStatus; }

    public ProcessingStatus getQuizStatus() { return quizStatus; }
    public void setQuizStatus(ProcessingStatus quizStatus) { this.quizStatus = quizStatus; }

    public ProcessingStatus getFlashcardStatus() { return flashcardStatus; }
    public void setFlashcardStatus(ProcessingStatus flashcardStatus) { this.flashcardStatus = flashcardStatus; }

    public ProcessingStatus getMindmapStatus() { return mindmapStatus; }
    public void setMindmapStatus(ProcessingStatus mindmapStatus) { this.mindmapStatus = mindmapStatus; }

    public ProcessingStatus getDiscussionStatus() { return discussionStatus; }
    public void setDiscussionStatus(ProcessingStatus discussionStatus) { this.discussionStatus = discussionStatus; }

    public ProcessingStatus getEmbeddingStatus() { return embeddingStatus; }
    public void setEmbeddingStatus(ProcessingStatus embeddingStatus) { this.embeddingStatus = embeddingStatus; }

    public ProcessingStatus getPodcastStatus() { return podcastStatus; }
    public void setPodcastStatus(ProcessingStatus podcastStatus) { this.podcastStatus = podcastStatus; }
}
