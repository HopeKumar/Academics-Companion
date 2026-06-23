package com.lms.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "slide_exports")
public class SlideExport {

    @Id
    private String id;
    
    @Indexed
    private String sourceId;
    
    @Indexed
    private String userId;
    
    private String pptFile;
    private String downloadUrl;
    
    private String pdfFile;
    private String pdfDownloadUrl;
    
    private String status;
    private String generatedAt;

    public SlideExport() {
        this.generatedAt = Instant.now().toString();
        this.status = "PROCESSING";
    }

    public SlideExport(String userId, String sourceId) {
        this();
        this.userId = userId;
        this.sourceId = sourceId;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getPptFile() { return pptFile; }
    public void setPptFile(String pptFile) { this.pptFile = pptFile; }
    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }
    public String getPdfFile() { return pdfFile; }
    public void setPdfFile(String pdfFile) { this.pdfFile = pdfFile; }
    public String getPdfDownloadUrl() { return pdfDownloadUrl; }
    public void setPdfDownloadUrl(String pdfDownloadUrl) { this.pdfDownloadUrl = pdfDownloadUrl; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(String generatedAt) { this.generatedAt = generatedAt; }
}
