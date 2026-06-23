package com.lms.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class FileStorageService {

    private static final Logger LOG = LoggerFactory.getLogger(FileStorageService.class);
    
    private final Path rootDir = Paths.get("uploads").toAbsolutePath().normalize();

    public void initDirectory(String dirName) {
        try {
            Path path = rootDir.resolve(dirName).normalize();
            if (!path.startsWith(rootDir)) {
                throw new SecurityException("Cannot create directory outside uploads directory");
            }
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                LOG.info("Created directory: {}", path);
            }
        } catch (Exception e) {
            LOG.error("Failed to create directory: {}", dirName, e);
            throw new RuntimeException("Could not create storage directory", e);
        }
    }

    public Path getPath(String subDir, String filename) {
        Path path = rootDir.resolve(subDir).resolve(filename).normalize();
        if (!path.startsWith(rootDir)) {
            throw new SecurityException("Path traversal attempt detected: " + filename);
        }
        return path;
    }

    public Resource loadFileAsResource(String subDir, String filename) throws FileNotFoundException {
        Path filePath = getPath(subDir, filename);
        
        if (!Files.exists(filePath)) {
            throw new FileNotFoundException("File not found: " + filename);
        }
        
        return new FileSystemResource(filePath.toFile());
    }
}
