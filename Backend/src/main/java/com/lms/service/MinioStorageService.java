package com.lms.service;

import com.lms.exception.StorageException;
import io.minio.*;
import io.minio.errors.MinioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
public class MinioStorageService {

    private static final Logger LOG = LoggerFactory.getLogger(MinioStorageService.class);

    private final MinioClient minioClient;
    private final com.lms.metrics.MetricsRegistry metricsRegistry;
    private boolean useFallback = false;
    private final Path fallbackDir = Paths.get("uploads", "minio-fallback").toAbsolutePath().normalize();

    @Value("${minio.bucket.name:campus-documents}")
    private String bucketName;

    public MinioStorageService(MinioClient minioClient, com.lms.metrics.MetricsRegistry metricsRegistry) {
        this.minioClient = minioClient;
        this.metricsRegistry = metricsRegistry;
    }

    @PostConstruct
    public void init() {
        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                LOG.info("Created MinIO bucket: {}", bucketName);
            }
        } catch (Exception e) {
            LOG.warn("MinIO client is not available (connection refused/error). Falling back to local filesystem storage.", e);
            useFallback = true;
            try {
                Files.createDirectories(fallbackDir);
            } catch (IOException ex) {
                LOG.error("Failed to create fallback directory", ex);
            }
        }
    }

    public boolean isMinioAvailable() {
        return !useFallback;
    }

    private Path getFallbackPath(String key) {
        Path path = fallbackDir.resolve(key).normalize();
        if (!path.startsWith(fallbackDir)) {
            throw new SecurityException("Path traversal attempt detected: " + key);
        }
        return path;
    }

    public void uploadFile(String key, MultipartFile file) {
        long startTime = System.currentTimeMillis();
        try {
            if (useFallback) {
                try {
                    Path dest = getFallbackPath(key);
                    Files.createDirectories(dest.getParent());
                    Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
                    LOG.info("[FALLBACK] Uploaded MultipartFile to key: {}", key);
                } catch (Exception e) {
                    throw new StorageException("Fallback upload failed for key: " + key, e);
                }
            } else {
                try (InputStream is = file.getInputStream()) {
                    minioClient.putObject(
                            PutObjectArgs.builder()
                                    .bucket(bucketName)
                                    .object(key)
                                    .stream(is, file.getSize(), -1)
                                    .contentType(file.getContentType())
                                    .build()
                    );
                    LOG.info("[MINIO] Uploaded MultipartFile to key: {}", key);
                } catch (Exception e) {
                    throw new StorageException("Failed to upload file to MinIO: " + key, e);
                }
            }
        } finally {
            metricsRegistry.recordStorage("write", System.currentTimeMillis() - startTime);
        }
    }

    public void uploadLocalFile(String key, Path localPath, String contentType) {
        long startTime = System.currentTimeMillis();
        try {
            if (useFallback) {
                try {
                    Path dest = getFallbackPath(key);
                    Files.createDirectories(dest.getParent());
                    Files.copy(localPath, dest, StandardCopyOption.REPLACE_EXISTING);
                    LOG.info("[FALLBACK] Uploaded local file to key: {}", key);
                } catch (Exception e) {
                    throw new StorageException("Fallback upload failed for key: " + key, e);
                }
            } else {
                try (InputStream is = Files.newInputStream(localPath)) {
                    minioClient.putObject(
                            PutObjectArgs.builder()
                                    .bucket(bucketName)
                                    .object(key)
                                    .stream(is, Files.size(localPath), -1)
                                    .contentType(contentType)
                                    .build()
                    );
                    LOG.info("[MINIO] Uploaded local file to key: {}", key);
                } catch (Exception e) {
                    throw new StorageException("MinIO upload failed for key: " + key, e);
                }
            }
        } finally {
            metricsRegistry.recordStorage("write", System.currentTimeMillis() - startTime);
        }
    }

    public boolean fileExists(String key) {
        if (useFallback) {
            return Files.exists(getFallbackPath(key));
        } else {
            try {
                minioClient.statObject(StatObjectArgs.builder().bucket(bucketName).object(key).build());
                return true;
            } catch (io.minio.errors.ErrorResponseException e) {
                if ("NoSuchKey".equals(e.errorResponse().code())) {
                    return false;
                }
                throw new StorageException("Error checking file existence in MinIO", e);
            } catch (Exception e) {
                throw new StorageException("Error checking file existence in MinIO", e);
            }
        }
    }

    public void deleteFile(String key) {
        long startTime = System.currentTimeMillis();
        try {
            if (useFallback) {
                try {
                    Files.deleteIfExists(getFallbackPath(key));
                    LOG.info("[FALLBACK] Deleted file with key: {}", key);
                } catch (Exception e) {
                    throw new StorageException("Fallback delete failed for key: " + key, e);
                }
            } else {
                try {
                    minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucketName).object(key).build());
                    LOG.info("[MINIO] Deleted file with key: {}", key);
                } catch (Exception e) {
                    throw new StorageException("Failed to delete file from MinIO: " + key, e);
                }
            }
        } finally {
            metricsRegistry.recordStorage("delete", System.currentTimeMillis() - startTime);
        }
    }

    public InputStream getFileStream(String key) {
        long startTime = System.currentTimeMillis();
        try {
            if (useFallback) {
                try {
                    return Files.newInputStream(getFallbackPath(key));
                } catch (Exception e) {
                    throw new StorageException("Fallback failed to get file stream for key: " + key, e);
                }
            } else {
                try {
                    return minioClient.getObject(GetObjectArgs.builder().bucket(bucketName).object(key).build());
                } catch (Exception e) {
                    throw new StorageException("Failed to download file from MinIO: " + key, e);
                }
            }
        } finally {
            metricsRegistry.recordStorage("read", System.currentTimeMillis() - startTime);
        }
    }

    public Resource getFileAsResource(String key) {
        String filename = Paths.get(key).getFileName().toString();
        if (useFallback) {
            Path path = getFallbackPath(key);
            try {
                if (!Files.exists(path)) {
                    throw new java.io.FileNotFoundException("File not found in fallback storage: " + key);
                }
                return new MinioStorageResource(Files.newInputStream(path), Files.size(path), filename);
            } catch (Exception e) {
                throw new StorageException("Failed to load local file: " + key, e);
            }
        } else {
            try {
                StatObjectResponse stat = minioClient.statObject(
                        StatObjectArgs.builder().bucket(bucketName).object(key).build()
                );
                InputStream is = minioClient.getObject(
                        GetObjectArgs.builder().bucket(bucketName).object(key).build()
                );
                return new MinioStorageResource(is, stat.size(), filename);
            } catch (Exception e) {
                throw new StorageException("Failed to load file from MinIO: " + key, e);
            }
        }
    }

    public static class MinioStorageResource extends AbstractResource {
        private final InputStream inputStream;
        private final long contentLength;
        private final String filename;

        public MinioStorageResource(InputStream inputStream, long contentLength, String filename) {
            this.inputStream = inputStream;
            this.contentLength = contentLength;
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }

        @Override
        public long contentLength() {
            return contentLength;
        }

        @Override
        public InputStream getInputStream() {
            return inputStream;
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public String getDescription() {
            return "MinIO object resource: " + filename;
        }
    }
}
