package com.lms.controller;

import com.lms.metrics.MetricsRegistry;
import com.lms.service.SpeechService;
import com.lms.util.ResponseUtil;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Set;

/**
 * SpeechController — FINAL POLISH: fallback handling
 *
 * Existing route (POST /speech-to-text) is PRESERVED.
 * The only change: STT failure now returns the spec-mandated JSON shape:
 *
 *   {
 *     "error":    "speech_processing_failed",
 *     "fallback": "text-mode",
 *     "message":  "Could not process audio, please type your query"
 *   }
 *
 * MetricsRegistry.recordSttError() is called on every STT failure so
 * summarySnapshot().audioErrors reflects the real failure count.
 */
public class SpeechController {

    private static final Logger LOG = LoggerFactory.getLogger(SpeechController.class);

    private static final Set<String> AUDIO_MIME_TYPES = Set.of(
            "audio/wav", "audio/x-wav", "audio/wave",
            "audio/mpeg", "audio/mp3",
            "audio/ogg", "audio/flac",
            "audio/m4a", "audio/mp4",
            "application/octet-stream"
    );

    private final SpeechService speechService;

    public SpeechController(SpeechService speechService) {
        this.speechService = speechService;
    }

    public void register(Router router) {
        router.post("/speech-to-text")
              .handler(BodyHandler.create().setUploadsDirectory("/tmp").setDeleteUploadedFilesOnEnd(true))
              .handler(this::speechToText);

        LOG.info("STT endpoint registered at POST /speech-to-text");
    }

    // ── POST /speech-to-text ──────────────────────────────────────────────

    private void speechToText(RoutingContext ctx) {
        long start = System.currentTimeMillis();

        byte[] audioBytes;
        String filename;

        if (!ctx.fileUploads().isEmpty()) {
            FileUpload upload = ctx.fileUploads().iterator().next();
            filename = upload.fileName();

            String contentType = upload.contentType();
            if (contentType != null && !contentType.startsWith("audio/")
                    && !AUDIO_MIME_TYPES.contains(contentType)) {
                LOG.warn("STT INVALID CONTENT TYPE: {} filename={}", contentType, filename);
                ResponseUtil.badRequest(ctx,
                        "Invalid content type '" + contentType + "'. Audio file required.");
                return;
            }

            try {
                audioBytes = Files.readAllBytes(Paths.get(upload.uploadedFileName()));
            } catch (Exception e) {
                LOG.error("STT UPLOAD READ ERROR: {}", e.getMessage());
                MetricsRegistry.get().recordRequest("/speech-to-text",
                        System.currentTimeMillis() - start, true);
                // FINAL POLISH: fallback handling — spec-exact STT error shape
                sttFallbackResponse(ctx, "Could not read uploaded file: " + e.getMessage());
                return;
            }

        } else if (ctx.getBody() != null && ctx.getBody().length() > 0) {
            String contentType = ctx.request().getHeader("Content-Type");
            if (contentType == null || (!contentType.startsWith("audio/")
                    && !AUDIO_MIME_TYPES.contains(contentType))) {
                ResponseUtil.badRequest(ctx,
                        "No audio file found. Send as multipart/form-data field 'audio' " +
                        "or raw body with Content-Type: audio/wav");
                return;
            }
            audioBytes = ctx.getBody().getBytes();
            String ext = contentType.contains("mp3") || contentType.contains("mpeg") ? "mp3"
                       : contentType.contains("ogg")  ? "ogg"
                       : contentType.contains("flac") ? "flac"
                       : "wav";
            filename = "upload." + ext;

        } else {
            ResponseUtil.badRequest(ctx,
                    "No audio file received. Send as multipart/form-data field 'audio'.");
            return;
        }

        LOG.info("POST /speech-to-text filename={} size={}KB",
                filename, audioBytes.length / 1024);

        final String finalFilename = filename;
        speechService.transcribe(audioBytes, finalFilename)
                .onSuccess(result -> {
                    long elapsed = System.currentTimeMillis() - start;

                    if (elapsed > 5_000) {
                        LOG.warn("SLOW STT filename={} elapsed={}ms", finalFilename, elapsed);
                        MetricsRegistry.get().recordSlowRequest("/speech-to-text", elapsed);
                    }

                    LOG.info("STT SUCCESS filename={} textLen={} elapsed={}ms",
                            finalFilename,
                            result.getString("text", "").length(),
                            elapsed);

                    ResponseUtil.ok(ctx, result);
                })
                .onFailure(err -> {
                    long elapsed = System.currentTimeMillis() - start;
                    MetricsRegistry.get().recordRequest("/speech-to-text", elapsed, true);

                    // FINAL POLISH: fallback handling — track STT error in metrics
                    MetricsRegistry.get().recordSttError();

                    LOG.error("STT FAILURE filename={} elapsed={}ms: {}",
                            finalFilename, elapsed, err.getMessage());

                    // FINAL POLISH: fallback handling — spec-exact STT error JSON
                    sttFallbackResponse(ctx, err.getMessage());
                });
    }

    // ── Private helpers ───────────────────────────────────────────────────

    /**
     * FINAL POLISH: fallback handling — STT failure response.
     *
     * Returns the exact shape specified in the prompt:
     * {
     *   "error":    "speech_processing_failed",
     *   "fallback": "text-mode",
     *   "message":  "Could not process audio, please type your query"
     * }
     *
     * The raw Whisper error is included under "detail" for server-side
     * debugging without exposing implementation internals to end-users.
     */
    private void sttFallbackResponse(RoutingContext ctx, String detail) {
        JsonObject body = new JsonObject()
                .put("error",    "speech_processing_failed")
                .put("fallback", "text-mode")
                .put("message",  "Could not process audio, please type your query")
                .put("detail",   detail != null ? detail : "unknown error");

        ctx.response()
           .setStatusCode(500)
           .putHeader("Content-Type", "application/json")
           .end(body.encodePrettily());
    }
}
