package com.lms.controller;

import com.lms.model.Document;
import com.lms.repository.DocumentRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping({"/debug", "/api/v1/debug"})
public class DebugController {

    private final DocumentRepository documentRepository;

    public DebugController(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @GetMapping("/source/{sourceId}")
    public ResponseEntity<Map<String, Object>> getDebugStatus(@PathVariable String sourceId) {
        try {
            Optional<Document> docOpt = documentRepository.findById(sourceId);
            if (docOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Document doc = docOpt.get();
            Map<String, Object> response = new HashMap<>();
            response.put("sourceId", doc.getId().toString());
            response.put("status", doc.getStatus().name());
            
            // Note: Since chunks and embeddings are stored separately, chunkCount could be retrieved from repositories.
            // For now, we will simulate or omit chunk count until we link it, or we could fetch it.
            // Let's assume the DB will eventually track it, or we just report what we have on Document.
            response.put("summaryStatus", doc.getSummaryStatus().name());
            response.put("flashcardStatus", doc.getFlashcardStatus().name());
            response.put("quizStatus", doc.getQuizStatus().name());
            response.put("mindmapStatus", doc.getMindmapStatus().name());
            response.put("discussionStatus", doc.getDiscussionStatus().name());
            response.put("embeddingStatus", doc.getEmbeddingStatus().name());
            response.put("processingTime", java.time.Duration.between(doc.getCreatedAt(), doc.getUpdatedAt()).toMillis());

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
