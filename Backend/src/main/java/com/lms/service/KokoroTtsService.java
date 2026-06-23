package com.lms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class KokoroTtsService {

    private static final Logger LOG = LoggerFactory.getLogger(KokoroTtsService.class);

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    public KokoroTtsService(AppProperties appProperties, ObjectMapper objectMapper) {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
    }

    public void generateAudio(List<Map<String, String>> script, String outputPath) throws Exception {
        LOG.info("Starting Kokoro TTS generation. Script size: {} segments", script.size());
        
        Path tempJsonFile = null;
        Process process = null;
        try {
            // Write segments to temporary JSON file
            tempJsonFile = Files.createTempFile("podcast-script-", ".json");
            objectMapper.writeValue(tempJsonFile.toFile(), script);

            String pythonPath = appProperties.getKokoro().getPythonPath();
            String scriptPath = appProperties.getKokoro().getScriptPath();
            int timeoutSec = appProperties.getKokoro().getTimeoutSec();

            LOG.info("Invoking Python Kokoro TTS script. pythonPath: '{}', scriptPath: '{}', timeout: {}s",
                    pythonPath, scriptPath, timeoutSec);

            List<String> command = new ArrayList<>();
            command.add(pythonPath);
            command.add(scriptPath);
            command.add("--input");
            command.add(tempJsonFile.toAbsolutePath().toString());
            command.add("--output");
            command.add(outputPath);

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            
            // Set working directory to project root or script directory if relative
            File scriptFile = new File(scriptPath);
            if (scriptFile.getParentFile() != null) {
                pb.directory(scriptFile.getParentFile());
            }

            process = pb.start();

            // Read the output logs on a background daemon thread to prevent blocking the timeout check
            final Process proc = process;
            Thread loggerThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        LOG.info("[Kokoro Script] {}", line);
                    }
                } catch (Exception e) {
                    LOG.debug("Error reading Kokoro script output: {}", e.getMessage());
                }
            });
            loggerThread.setDaemon(true);
            loggerThread.setName("kokoro-log-reader");
            loggerThread.start();

            boolean completed = process.waitFor(timeoutSec, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new RuntimeException("Kokoro TTS generation process timed out after " + timeoutSec + " seconds.");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new RuntimeException("Kokoro TTS generation script failed with exit code " + exitCode);
            }

            File outputFile = new File(outputPath);
            if (!outputFile.exists() || outputFile.length() == 0) {
                throw new RuntimeException("Kokoro TTS generation completed but output file is missing or empty.");
            }

            LOG.info("Kokoro TTS generation completed successfully. Output file: {}", outputPath);

        } finally {
            if (tempJsonFile != null) {
                try {
                    Files.deleteIfExists(tempJsonFile);
                } catch (Exception e) {
                    LOG.warn("Failed to delete temporary JSON script file: {}", tempJsonFile, e);
                }
            }
        }
    }
}
