package com.lms.controller;

import com.lms.service.AIHealthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping({"/health", "/api/v1/health", "/api/health"})
public class AIHealthController {
    
    private final AIHealthService aiHealthService;

    public AIHealthController(AIHealthService aiHealthService) {
        this.aiHealthService = aiHealthService;
    }

    @GetMapping("/ai")
    public ResponseEntity<Map<String, Object>> getAIHealth() {
        Map<String, Object> detailed = aiHealthService.getDetailedHealth();
        
        Map<String, Object> response = new HashMap<>();
        response.put("ollama", detailed.getOrDefault("status", "DOWN"));
        response.put("model", "mistral");
        
        boolean modelAvail = Boolean.TRUE.equals(detailed.getOrDefault("primaryModelAvailable", false));
        boolean ollamaUp = "UP".equals(detailed.get("status"));
        
        response.put("status", (ollamaUp && modelAvail) ? "READY" : "DOWN");
        
        return ResponseEntity.ok(response);
    }
}
