package com.lms.service;

import com.lms.model.Document;
import com.lms.model.DocumentStatus;
import com.lms.repository.DocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DocumentCleanupJobTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private MinioStorageService minioStorageService;

    @InjectMocks
    private DocumentCleanupJob documentCleanupJob;

    @Test
    void testCleanupStaleUploadingDocuments() {
        Document staleDoc = new Document();
        staleDoc.setId(UUID.randomUUID().toString());
        staleDoc.setOriginalFilename("stale.pdf");

        when(documentRepository.findByStatusAndCreatedAtBefore(eq(DocumentStatus.UPLOADING), any(LocalDateTime.class)))
                .thenReturn(List.of(staleDoc));
        when(minioStorageService.fileExists(anyString())).thenReturn(true);
        doNothing().when(minioStorageService).deleteFile(anyString());
        doNothing().when(documentRepository).delete(any(Document.class));

        documentCleanupJob.cleanupStaleUploadingDocuments();

        verify(minioStorageService, times(1)).deleteFile(anyString());
        verify(documentRepository, times(1)).delete(staleDoc);
    }
}
