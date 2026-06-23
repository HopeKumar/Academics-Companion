package com.lms.service;

import com.lms.exception.DuplicateDocumentException;
import com.lms.exception.StorageException;
import com.lms.model.Document;
import com.lms.model.DocumentStatus;
import com.lms.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.util.Optional;

@Service
public class DocumentUploadService {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentUploadService.class);

    private final DocumentRepository documentRepository;
    private final MinioStorageService minioStorageService;
    private final DocumentProcessingService documentProcessingService;
    private final AuditLogService auditLogService;

    public DocumentUploadService(DocumentRepository documentRepository,
                                 MinioStorageService minioStorageService,
                                 DocumentProcessingService documentProcessingService,
                                 AuditLogService auditLogService) {
        this.documentRepository = documentRepository;
        this.minioStorageService = minioStorageService;
        this.documentProcessingService = documentProcessingService;
        this.auditLogService = auditLogService;
    }

    @Transactional(noRollbackFor = StorageException.class)
    public Document uploadDocument(MultipartFile file, String ownerId) throws Exception {
        String fileHash = calculateSha256(file);

        Optional<Document> existingDoc = documentRepository.findByFileHashSha256(fileHash);
        if (existingDoc.isPresent()) {
            throw new DuplicateDocumentException("Document with this content already exists.");
        }

        Document document = new Document();
        document.setOwnerId(ownerId);
        document.setOriginalFilename(file.getOriginalFilename());
        document.setFileHashSha256(fileHash);
        document.setSizeBytes(file.getSize());
        document.setMimeType(file.getContentType());
        document.setStatus(DocumentStatus.UPLOADING);

        document = documentRepository.save(document);
        
        String storageKey = "documents/" + document.getId() + "/" + file.getOriginalFilename();

        try {
            minioStorageService.uploadFile(storageKey, file);
        } catch (Exception e) {
            LOG.error("MinIO upload failed for document {}", document.getId(), e);
            document.setStatus(DocumentStatus.FAILED);
            documentRepository.save(document);
            throw new StorageException("Failed to upload file to storage. Transaction will roll back.", e);
        }

        document.setStatus(DocumentStatus.UPLOADED);
        document = documentRepository.save(document);

        auditLogService.log(ownerId, "UPLOAD_DOCUMENT", document.getId(),
                "Uploaded document: " + file.getOriginalFilename() + " (" + file.getSize() + " bytes)", null);

        // Trigger Async Processing
        documentProcessingService.processDocument(document.getId());

        return document;
    }

    private String calculateSha256(MultipartFile file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(file.getBytes());
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
