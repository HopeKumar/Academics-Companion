package com.lms.controller;

import com.lms.dto.DocumentChunkResponse;
import com.lms.dto.DocumentReaderResponse;
import com.lms.model.User;
import com.lms.service.AuthService;
import com.lms.service.DocumentChunkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/sources", "/api/v1/sources"})
public class DocumentChunkController {

    private final DocumentChunkService chunkService;
    private final AuthService authService;

    @Autowired
    public DocumentChunkController(DocumentChunkService chunkService, AuthService authService) {
        this.chunkService = chunkService;
        this.authService = authService;
    }

    @GetMapping("/{sourceId}/chunks")
    public ResponseEntity<Page<DocumentChunkResponse>> getChunks(
            @PathVariable("sourceId") String sourceId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size,
            @RequestParam(value = "q", required = false) String query) {

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);

        Page<DocumentChunkResponse> chunkPage = chunkService.getChunks(
                sourceId,
                user.getId(),
                query,
                PageRequest.of(page, size)
        );

        return ResponseEntity.ok(chunkPage);
    }

    @GetMapping("/{sourceId}/reader")
    public ResponseEntity<DocumentReaderResponse> getReaderData(
            @PathVariable("sourceId") String sourceId) {

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = authService.getUserByUsername(username);

        DocumentReaderResponse readerResponse = chunkService.getReaderData(sourceId, user.getId());

        return ResponseEntity.ok(readerResponse);
    }
}
