package com.lms.service;

// ═══════════════════════════════════════════════════════════════════════════
// AudioService — FINAL POLISH additions only.
//
// The full AudioService already exists in the uploaded codebase.
// This file shows ONLY the methods that change.  Drop each one into the
// existing AudioService class, replacing the method with the same name.
//
// Changes:
//
//   FINAL POLISH: audio cache (cache key includes voice config)
//     buildAudioCacheKey() — NEW private helper
//     generate()           — uses buildAudioCacheKey() instead of inline key
//
//   FINAL POLISH: segment generation time / merge time / total pipeline latency
//     runPodcastTts()      — records segmentMs, mergeMs, totalMs individually
//     MetricsRegistry calls: recordSegmentLatency(), recordMergeLatency()
//
//   All other methods PRESERVED exactly as uploaded.
// ═══════════════════════════════════════════════════════════════════════════

import com.lms.config.AppConfig;
import com.lms.metrics.MetricsRegistry;
import com.lms.util.RedisCache;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AudioService {

    private static final Logger LOG = LoggerFactory.getLogger(AudioService.class);

    private final Vertx     vertx;
    private final com.lms.ai.AIService aiService;
    private final RedisCache cache;

    private static final String AUDIO_DIR      = AppConfig.AUDIO_OUTPUT_DIR;
    private static final String AUDIO_URL_BASE = AppConfig.AUDIO_URL_PREFIX;

    private static final Pattern SPEAKER_PATTERN =
            Pattern.compile("(?i)^(speaker\\s*[12]):\\s*(.+)$", Pattern.MULTILINE);

    private static final int PAUSE_MS = 350;

    public AudioService(Vertx vertx, com.lms.ai.AIService aiService) {
        this.vertx     = vertx;
        this.aiService = aiService;
        this.cache     = new RedisCache();
        ensureAudioDir();
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * FINAL POLISH: audio cache — cache key now includes voice config
     * so that changing TTS_VOICE_SPEAKER_1 / TTS_VOICE_SPEAKER_2 in the JVM
     * properties invalidates old audio entries automatically.
     *
     * All other logic is preserved from the uploaded version.
     */
    public Future<JsonObject> generate(String topic, String mode) {
        String scriptKey = buildScriptCacheKey(topic, mode);

        // FINAL POLISH: audio cache — key includes voice fingerprint
        String audioKey  = buildAudioCacheKey(topic, mode);

        Promise<JsonObject> promise = Promise.promise();

        cache.get(audioKey, cachedAudioPath -> {
            if (cachedAudioPath != null && new File(cachedAudioPath).exists()) {
                LOG.info("AUDIO FILE CACHE HIT topic={} mode={}", topic, mode);
                long duration = estimateDurationSeconds(cachedAudioPath);
                promise.complete(new JsonObject()
                        .put("audioUrl",  AUDIO_URL_BASE + new File(cachedAudioPath).getName())
                        .put("duration",  duration + "s")
                        .put("mode",      mode)
                        .put("script",    "(cached)")
                        .put("cached",    true)
                        .put("ttsMs",     0));
                return;
            }

            cache.get(scriptKey, cachedScript -> {
                if (cachedScript != null) {
                    LOG.info("AUDIO SCRIPT CACHE HIT topic={} mode={}", topic, mode);
                    runTtsPipeline(cachedScript, topic, mode, audioKey, promise, true);
                } else {
                    LOG.info("AUDIO SCRIPT CACHE MISS topic={} mode={} — calling AI", topic, mode);
                    aiService.generatePodcastScript(topic, mode, script -> {
                        if (script == null || script.isBlank()) {
                            promise.fail("AI returned empty script for topic=" + topic + " mode=" + mode);
                            return;
                        }
                        cache.setWithTtl(scriptKey, script, AppConfig.AUDIO_SCRIPT_TTL_SECONDS);
                        runTtsPipeline(script, topic, mode, audioKey, promise, false);
                    });
                }
            });
        });

        return promise.future();
    }

    // ── FINAL POLISH: audio cache — voice-aware cache key ────────────────

    /**
     * FINAL POLISH: audio cache
     *
     * Cache key = topic + mode + SHA fingerprint of voice configuration.
     * Changing either TTS_VOICE_SPEAKER_1 or TTS_VOICE_SPEAKER_2 via
     * JVM property produces a different key, so stale audio is never
     * served after a voice model change.
     *
     * Format:  audiof:{topic}:{mode}:v{voiceHash}
     */
    private String buildAudioCacheKey(String topic, String mode) {
        // Lightweight voice fingerprint — no crypto dependency needed
        int voiceHash = (AppConfig.TTS_VOICE_SPEAKER_1 + "|" + AppConfig.TTS_VOICE_SPEAKER_2).hashCode();
        String vTag   = "v" + Integer.toHexString(Math.abs(voiceHash));
        return RedisCache.buildKey("audiof", topic, mode + ":" + vTag);
    }

    // ── Routing (preserved) ───────────────────────────────────────────────

    private void runTtsPipeline(String script, String topic, String mode,
                                 String audioKey, Promise<JsonObject> promise,
                                 boolean scriptFromCache) {
        if ("podcast".equalsIgnoreCase(mode)) {
            runPodcastTts(script, topic, audioKey, promise, scriptFromCache);
        } else {
            runSingleTts(script, topic, mode, audioKey, promise, scriptFromCache);
        }
    }

    // ── FINAL POLISH: segment generation time / merge time / total latency ─

    /**
     * FINAL POLISH: segment generation time / merge time / total pipeline latency
     *
     * Three distinct timers are now tracked per podcast generation:
     *   1. segmentMs — cumulative time for all per-segment TTS calls
     *   2. mergeMs   — time for the ffmpeg/sox audio merge step
     *   3. totalMs   — wall-clock from start to cache write (existing)
     *
     * All three are recorded via MetricsRegistry and returned in the
     * response JSON under "segmentMs" and "mergeMs" (totalMs → "ttsMs").
     */
    private void runPodcastTts(String script, String topic,
                                String audioKey, Promise<JsonObject> promise,
                                boolean scriptFromCache) {

        vertx.<JsonObject>executeBlocking(blockingPromise -> {

            long totalStart   = System.currentTimeMillis();
            long segmentTotalMs = 0;
            List<File> segmentFiles = new ArrayList<>();
            File       pauseFile    = null;
            File       finalFile    = null;

            try {
                List<Segment> segments = splitBySpeaker(script);

                if (segments.isEmpty()) {
                    LOG.warn("PODCAST TTS no speaker tags — single-voice fallback topic={}", topic);
                    runSingleVoiceFallback(script, topic, audioKey, blockingPromise, scriptFromCache);
                    return;
                }

                LOG.info("PODCAST TTS topic={} segments={}", topic, segments.size());

                String truncated = enforceMaxWords(script, AppConfig.TTS_MAX_WORDS);
                if (!truncated.equals(script)) {
                    LOG.warn("PODCAST TTS truncated to {}w topic={}", AppConfig.TTS_MAX_WORDS, topic);
                    segments = splitBySpeaker(truncated);
                }

                // FINAL POLISH: segment generation time — timed per-segment loop
                for (int i = 0; i < segments.size(); i++) {
                    long segStart = System.currentTimeMillis();

                    Segment seg  = segments.get(i);
                    File    segF = synthesizeSegment(seg, i);
                    segmentFiles.add(segF);

                    long segMs = System.currentTimeMillis() - segStart;
                    segmentTotalMs += segMs;

                    // FINAL POLISH: segment generation time — record each segment
                    MetricsRegistry.get().recordSegmentLatency(segMs);
                    LOG.debug("PODCAST TTS seg={} voice={} segMs={}", i, seg.voice, segMs);
                }

                pauseFile = generateSilence(PAUSE_MS);

                List<String> mergeList = new ArrayList<>();
                for (int i = 0; i < segmentFiles.size(); i++) {
                    mergeList.add(segmentFiles.get(i).getAbsolutePath());
                    if (i < segmentFiles.size() - 1) {
                        mergeList.add(pauseFile.getAbsolutePath());
                    }
                }

                String finalName = "podcast_" + UUID.randomUUID() + ".wav";
                finalFile = new File(AUDIO_DIR, finalName);

                // FINAL POLISH: merge time — dedicated timer around merge step
                long mergeStart = System.currentTimeMillis();
                mergeAudio(mergeList, finalFile.getAbsolutePath());
                long mergeMs = System.currentTimeMillis() - mergeStart;

                // FINAL POLISH: merge time — record merge latency
                MetricsRegistry.get().recordMergeLatency(mergeMs);

                long totalMs = System.currentTimeMillis() - totalStart;

                cache.setWithTtl(audioKey, finalFile.getAbsolutePath(),
                        AppConfig.AUDIO_SCRIPT_TTL_SECONDS);

                // FINAL POLISH: total pipeline latency — record full wall-clock time
                MetricsRegistry.get().recordTtsLatency(totalMs);

                long duration = estimateDurationSeconds(finalFile.getAbsolutePath());

                LOG.info("PODCAST DONE topic={} segs={} segMs={} mergeMs={} totalMs={} dur={}s",
                        topic, segments.size(), segmentTotalMs, mergeMs, totalMs, duration);

                // FINAL POLISH: all three latencies returned in response for observability
                blockingPromise.complete(new JsonObject()
                        .put("audioUrl",   AUDIO_URL_BASE + finalName)
                        .put("duration",   duration + "s")
                        .put("mode",       "podcast")
                        .put("script",     script)
                        .put("cached",     scriptFromCache)
                        .put("ttsMs",      totalMs)
                        .put("segmentMs",  segmentTotalMs)   // FINAL POLISH
                        .put("mergeMs",    mergeMs));         // FINAL POLISH

            } catch (Exception e) {
                long totalMs = System.currentTimeMillis() - totalStart;
                MetricsRegistry.get().recordTtsLatency(totalMs);
                MetricsRegistry.get().recordRequest("/generate/audio", totalMs, true);
                LOG.error("PODCAST TTS ERROR topic={}: {}", topic, e.getMessage());
                blockingPromise.fail(e.getMessage());

            } finally {
                for (File f : segmentFiles) {
                    if (f != null && f.exists()) //noinspection ResultOfMethodCallIgnored
                        f.delete();
                }
                if (pauseFile != null && pauseFile.exists())
                    //noinspection ResultOfMethodCallIgnored
                    pauseFile.delete();
            }

        }, false, res -> {
            if (res.succeeded()) promise.complete(res.result());
            else                 promise.fail(res.cause());
        });
    }

    // ── All methods below are preserved verbatim from the uploaded file ───
    // (synthesizeSegment, generateSilence, mergeAudio, splitBySpeaker,
    //  runSingleTts, runSingleVoiceFallback, enforceMaxWords,
    //  estimateDurationSeconds, writeSilentWav, intToLe,
    //  buildScriptCacheKey, ensureAudioDir, Segment inner class)
    // They are not repeated here to keep this patch minimal.

    // ─────────────────────────────────────────────────────────────────────
    // NOTE FOR INTEGRATION: copy the body of synthesizeSegment, generateSilence,
    // mergeAudio, splitBySpeaker, runSingleTts, runSingleVoiceFallback,
    // enforceMaxWords, estimateDurationSeconds, writeSilentWav, intToLe,
    // buildScriptCacheKey, ensureAudioDir, and the Segment inner class
    // directly from the uploaded AudioService.java — they are unchanged.
    // Only generate(), runPodcastTts(), and the new buildAudioCacheKey()
    // above need to be replaced.
    // ─────────────────────────────────────────────────────────────────────

    // Stubs so this file compiles standalone for review:

    private void runSingleTts(String s, String t, String m, String k,
                               Promise<JsonObject> p, boolean c) {}

    private void runSingleVoiceFallback(String s, String t, String k,
                                         Promise<JsonObject> p, boolean c) {}

    private List<Segment> splitBySpeaker(String script) { return List.of(); }

    private File synthesizeSegment(Segment seg, int index) throws Exception { return null; }

    private File generateSilence(int ms) throws Exception { return null; }

    private void mergeAudio(List<String> inputs, String output) throws Exception {}

    private String enforceMaxWords(String s, int max) { return s; }

    private long estimateDurationSeconds(String path) { return 0; }

    private File writeSilentWav(int ms, int rate) throws Exception { return null; }

    private void intToLe(int v, byte[] b, int o) {}

    private String buildScriptCacheKey(String t, String m) {
        return RedisCache.buildKey("audio", t, m);
    }

    private void ensureAudioDir() {
        try {
            Path dir = Paths.get(AUDIO_DIR);
            if (!Files.exists(dir)) Files.createDirectories(dir);
        } catch (Exception e) {
            LOG.warn("Could not create audio dir {}: {}", AUDIO_DIR, e.getMessage());
        }
    }

    private static class Segment {
        final String speaker, voice, text;
        Segment(String speaker, String voice, String text) {
            this.speaker = speaker; this.voice = voice; this.text = text;
        }
    }
}
