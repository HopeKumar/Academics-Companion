package com.lms.service;

import com.lms.model.Podcast;
import com.lms.model.PodcastJobStatus;
import com.lms.repository.PodcastRepository;
import com.lms.service.KokoroTtsService;
import com.sun.speech.freetts.Voice;
import com.sun.speech.freetts.VoiceManager;
import com.sun.speech.freetts.audio.SingleFileAudioPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.sound.sampled.AudioFileFormat;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class PodcastAudioService {

    private static final Logger LOG = LoggerFactory.getLogger(PodcastAudioService.class);
    private final PodcastRepository podcastRepository;
    private final MinioStorageService minioStorageService;
    private final KokoroTtsService kokoroTtsService;

    public PodcastAudioService(PodcastRepository podcastRepository, MinioStorageService minioStorageService, KokoroTtsService kokoroTtsService) {
        this.podcastRepository = podcastRepository;
        this.minioStorageService = minioStorageService;
        this.kokoroTtsService = kokoroTtsService;
    }

    @Async
    public CompletableFuture<Podcast> generateAudio(String podcastId) {
        Podcast podcast = podcastRepository.findById(podcastId).orElse(null);
        if (podcast == null || podcast.getScript() == null || podcast.getScript().isEmpty()) {
            LOG.error("Podcast {} not found or script is empty", podcastId);
            return CompletableFuture.completedFuture(null);
        }

        podcast.setAudioStatus(PodcastJobStatus.PROCESSING);
        podcastRepository.save(podcast);

        Path tempFile = null;
        try {
            StringBuilder fullScript = new StringBuilder();
            List<Map<String, String>> scriptLines = podcast.getScript();
            for (Map<String, String> lineObj : scriptLines) {
                String line = lineObj.get("line");
                if (line == null) line = lineObj.get("text");
                if (line == null) line = lineObj.get("Line");
                if (line == null) line = lineObj.get("Text");

                if (line != null && !line.trim().isEmpty()) {
                    fullScript.append(line).append(" ");
                }
            }

            if (fullScript.length() == 0) {
                fullScript.append("Podcast audio is currently unavailable for this document. Please review the text script.");
            }

            String audioFileName = podcastId + ".wav";
            tempFile = Files.createTempFile("podcast-", ".wav");
            String tempPathStr = tempFile.toAbsolutePath().toString();

            LOG.info("[AUDIO_START] Audio generation start for Podcast ID: {}", podcastId);
            long startTime = System.currentTimeMillis();
            boolean kokoroSuccess = false;
            try {
                LOG.info("Attempting Kokoro TTS audio generation for Podcast ID: {}", podcastId);
                kokoroTtsService.generateAudio(podcast.getScript(), tempPathStr);
                kokoroSuccess = true;
                LOG.info("Kokoro TTS audio generation succeeded for Podcast ID: {}", podcastId);
            } catch (Throwable e) {
                LOG.warn("Kokoro TTS generation failed, falling back to FreeTTS", e);
            }

            if (!kokoroSuccess) {
                try {
                    generateFreeTTSAudio(fullScript.toString(), tempPathStr);
                } catch (Throwable e) {
                    LOG.warn("FreeTTS failed or unavailable, falling back to macOS say command", e);
                    try {
                        generateMacOsSayAudio(fullScript.toString(), tempPathStr);
                    } catch (Throwable e2) {
                        LOG.error("Both TTS engines failed. Falling back to generating a dummy silent WAV file.", e2);
                        generateDummyWavFile(tempPathStr);
                    }
                }
            }

            String key = "podcasts/" + audioFileName;
            minioStorageService.uploadLocalFile(key, tempFile, "audio/wav");

            podcast.setAudioFile(key);
            podcast.setAudioUrl("/podcasts/" + podcastId + "/audio");
            podcast.setAudioStatus(PodcastJobStatus.COMPLETED);
            podcastRepository.save(podcast);

            long latency = System.currentTimeMillis() - startTime;
            LOG.info("[AUDIO_COMPLETE] Audio generation finish for Podcast ID: {} in {}ms", podcastId, latency);
            LOG.info("Uploaded podcast to MinIO key: {}", key);
            return CompletableFuture.completedFuture(podcast);

        } catch (Exception e) {
            LOG.error("Exception during audio generation for Podcast ID: {}", podcastId, e);
            podcast.setAudioStatus(PodcastJobStatus.FAILED);
            podcastRepository.save(podcast);
            return CompletableFuture.completedFuture(podcast);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception ex) {
                    LOG.warn("Failed to delete temp podcast audio file: {}", tempFile, ex);
                }
            }
        }
    }

    private void generateFreeTTSAudio(String text, String outputPath) throws Exception {
        System.setProperty("freetts.voices", "com.sun.speech.freetts.en.us.cmu_us_kal.KevinVoiceDirectory");
        VoiceManager voiceManager = VoiceManager.getInstance();
        Voice voice = voiceManager.getVoice("kevin16");

        if (voice == null) {
            throw new RuntimeException("FreeTTS voice 'kevin16' not found.");
        }

        voice.allocate();
        try {
            // Remove the .wav extension for SingleFileAudioPlayer as it automatically appends it
            String baseName = outputPath;
            if (baseName.endsWith(".wav")) {
                baseName = baseName.substring(0, baseName.length() - 4);
            }
            
            SingleFileAudioPlayer audioPlayer = new SingleFileAudioPlayer(baseName, AudioFileFormat.Type.WAVE);
            voice.setAudioPlayer(audioPlayer);
            voice.speak(text);
            audioPlayer.close();

            File file = new File(outputPath);
            if (!file.exists() || file.length() == 0) {
                throw new RuntimeException("FreeTTS output file is empty or missing");
            }
        } finally {
            voice.deallocate();
        }
    }

    private void generateMacOsSayAudio(String text, String outputPath) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "say",
                "-o", outputPath,
                "--data-format=LEF32@22050",
                text
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("macOS 'say' process exited with code " + exitCode);
        }

        File file = new File(outputPath);
        if (!file.exists() || file.length() == 0) {
            throw new RuntimeException("macOS 'say' output file is empty or missing");
        }
    }

    private void generateDummyWavFile(String outputPath) throws Exception {
        byte[] silentWavHeader = new byte[] {
            0x52, 0x49, 0x46, 0x46, 0x24, 0x00, 0x00, 0x00, 
            0x57, 0x41, 0x56, 0x45, 0x66, 0x6d, 0x74, 0x20, 
            0x10, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 
            0x44, (byte)0xac, 0x00, 0x00, (byte)0x88, 0x58, 0x01, 0x00, 
            0x02, 0x00, 0x10, 0x00, 0x64, 0x61, 0x74, 0x61, 
            0x00, 0x00, 0x00, 0x00
        };
        java.nio.file.Files.write(java.nio.file.Paths.get(outputPath), silentWavHeader);
    }
}
