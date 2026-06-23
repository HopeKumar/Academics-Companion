package com.lms.config;

import com.lms.service.FileStorageService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class FileStorageConfig {

    private static final Logger LOG = LoggerFactory.getLogger(FileStorageConfig.class);

    private static final List<String> REQUIRED_DIRECTORIES = List.of(
            "", // The root uploads dir
            "podcasts",
            "slides",
            "exports"
    );

    private final FileStorageService fileStorageService;

    public FileStorageConfig(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @PostConstruct
    public void initDirectories() {
        for (String dirName : REQUIRED_DIRECTORIES) {
            fileStorageService.initDirectory(dirName);
        }
        LOG.info("All required storage directories initialized.");
    }
}
