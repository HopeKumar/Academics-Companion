package com.lms.controller;

import com.lms.service.AIHealthService;
import com.lms.service.OCRService;
import com.lms.service.MinioStorageService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

@RestController
@RequestMapping({"/health", "/api/v1/health", "/api/system"})
public class HealthController {

    private final MongoTemplate mongoTemplate;
    private final AIHealthService aiHealthService;
    private final OCRService ocrService;
    private final MinioStorageService minioStorageService;
    private final Executor aiTaskExecutor;

    public HealthController(MongoTemplate mongoTemplate,
                            AIHealthService aiHealthService,
                            OCRService ocrService,
                            MinioStorageService minioStorageService,
                            @Qualifier("aiTaskExecutor") Executor aiTaskExecutor) {
        this.mongoTemplate = mongoTemplate;
        this.aiHealthService = aiHealthService;
        this.ocrService = ocrService;
        this.minioStorageService = minioStorageService;
        this.aiTaskExecutor = aiTaskExecutor;
    }

    @GetMapping
    public ResponseEntity<String> getHealth() {
        return ResponseEntity.ok("OK");
    }

    @GetMapping("/details")
    public ResponseEntity<Map<String, String>> getHealthDetails() {
        Map<String, String> details = new HashMap<>();
        details.put("status", "UP");
        details.put("service", "adaptive-backend");
        return ResponseEntity.ok(details);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getSystemHealth() {
        Map<String, Object> health = new HashMap<>();
        
        // Mongo status
        String mongoStatus = "DOWN";
        try {
            mongoTemplate.getDb().runCommand(new org.bson.Document("ping", 1));
            mongoStatus = "UP";
        } catch (Exception e) {
            // ignore
        }
        health.put("Mongo", mongoStatus);

        // Ollama status
        String ollamaStatus = aiHealthService.getHealthStatus();
        health.put("Ollama", ollamaStatus);

        // OCR status
        String ocrStatus = ocrService.isAvailable() ? "UP" : "DOWN";
        health.put("OCR", ocrStatus);

        // Storage status
        String storageStatus = minioStorageService.isMinioAvailable() ? "UP" : "DOWN";
        health.put("Storage", storageStatus);

        // Queue status
        String queueStatus = "DOWN";
        if (aiTaskExecutor instanceof ThreadPoolTaskExecutor) {
            ThreadPoolTaskExecutor tp = (ThreadPoolTaskExecutor) aiTaskExecutor;
            queueStatus = tp.getThreadPoolExecutor().isShutdown() ? "DOWN" : "UP";
        }
        health.put("Queue", queueStatus);

        // AI Model status
        Map<String, Object> detailed = aiHealthService.getDetailedHealth();
        boolean primaryModelAvailable = Boolean.TRUE.equals(detailed.get("primaryModelAvailable"));
        String aiModelStatus = primaryModelAvailable ? "UP" : "DOWN";
        health.put("AI Model", aiModelStatus);

        return ResponseEntity.ok(health);
    }
}
