package com.lms.controller;

import com.lms.model.Document;
import com.lms.model.DocumentStatus;
import com.lms.repository.DocumentRepository;
import com.lms.service.DocumentUploadService;
import com.lms.exception.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/documents", "/documents"})
public class DocumentController {

    private final DocumentUploadService documentUploadService;
    private final DocumentRepository documentRepository;
    private final com.lms.service.DocumentProcessingService documentProcessingService;

    public DocumentController(DocumentUploadService documentUploadService, DocumentRepository documentRepository, com.lms.service.DocumentProcessingService documentProcessingService) {
        this.documentUploadService = documentUploadService;
        this.documentRepository = documentRepository;
        this.documentProcessingService = documentProcessingService;
    }

    @PostMapping
    public ResponseEntity<Document> uploadDocument(@RequestParam("file") MultipartFile file,
                                                   @RequestParam("ownerId") String ownerId) throws Exception {
        Document uploaded = documentUploadService.uploadDocument(file, ownerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(uploaded);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Document> getDocument(@PathVariable String id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document", id));
        return ResponseEntity.ok(document);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> softDeleteDocument(@PathVariable String id) {
        documentProcessingService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }
}
