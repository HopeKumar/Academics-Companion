package com.lms.service;

import com.lms.exception.DuplicateDocumentException;
import com.lms.exception.StorageException;
import com.lms.model.Document;
import com.lms.model.DocumentStatus;
import com.lms.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DocumentUploadServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private MinioStorageService minioStorageService;

    @Mock
    private DocumentProcessingService documentProcessingService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private DocumentUploadService documentUploadService;

    private MockMultipartFile mockFile;
    private String ownerId = "student-123";

    @BeforeEach
    void setUp() {
        mockFile = new MockMultipartFile("file", "test.pdf", "application/pdf", "dummy content".getBytes());
    }

    @Test
    void testUploadDocumentSuccess() throws Exception {
        when(documentRepository.findByFileHashSha256(anyString())).thenReturn(Optional.empty());

        Document doc = new Document();
        doc.setId(UUID.randomUUID().toString());
        doc.setStatus(DocumentStatus.UPLOADING);

        when(documentRepository.save(any(Document.class))).thenReturn(doc);
        doNothing().when(minioStorageService).uploadFile(anyString(), any());
        doNothing().when(documentProcessingService).processDocument(any());

        Document result = documentUploadService.uploadDocument(mockFile, ownerId);

        assertNotNull(result);
        assertEquals(DocumentStatus.UPLOADED, result.getStatus());
        verify(minioStorageService, times(1)).uploadFile(anyString(), any());
        verify(documentRepository, times(2)).save(any(Document.class));
        verify(documentProcessingService, times(1)).processDocument(any());
    }

    @Test
    void testUploadDocumentDuplicate() {
        when(documentRepository.findByFileHashSha256(anyString())).thenReturn(Optional.of(new Document()));

        assertThrows(DuplicateDocumentException.class, () -> {
            documentUploadService.uploadDocument(mockFile, ownerId);
        });

        verify(documentRepository, never()).save(any());
        verify(minioStorageService, never()).uploadFile(anyString(), any());
    }

    @Test
    void testUploadDocumentStorageFailure() throws Exception {
        when(documentRepository.findByFileHashSha256(anyString())).thenReturn(Optional.empty());

        Document doc = new Document();
        doc.setId(UUID.randomUUID().toString());
        when(documentRepository.save(any(Document.class))).thenReturn(doc);
        
        doThrow(new StorageException("MinIO down")).when(minioStorageService).uploadFile(anyString(), any());

        assertThrows(StorageException.class, () -> {
            documentUploadService.uploadDocument(mockFile, ownerId);
        });

        assertEquals(DocumentStatus.FAILED, doc.getStatus());
        verify(documentRepository, times(2)).save(any(Document.class));
        verify(documentProcessingService, never()).processDocument(any());
    }
}
