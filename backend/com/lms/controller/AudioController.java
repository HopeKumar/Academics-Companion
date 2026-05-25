package com.lms.controller;

import com.lms.config.AppConfig;
import com.lms.metrics.MetricsRegistry;
import com.lms.service.AudioService;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * AudioController — FINAL POLISH additions only.
 *
 * All existing wiring (POST /generate/audio) is PRESERVED.
 *
 * FINAL POLISH: fallback handling
 *   TTS failures now return structured JSON:
 *     { "error": "audio_generation_failed",
 *       "fallback": "text-mode",
 *       "message": "Audio unavailable, returning text response" }
 *
 *   AI failures (empty script) now return:
 *     { "error": "script_generation_failed",
 *       "fallback": "heuristic-mode",
 *       "message": "AI unavailable, try again shortly" }
 *
 * FINAL POLISH: streaming endpoint
 *   GET /audio/stream/:file
 *   Streams the file in chunks with correct headers.
 *   Does NOT touch POST /generate/audio.
 */
public class AudioController {

    private static final Logger LOG = LoggerFactory.getLogger(AudioController.class);

    private final AudioService audioService;
    // Vert.x instance is injected via constructor for streaming
    private io.vertx.core.Vertx vertx;

    public AudioController(AudioService audioService) {
        this.audioService = audioService;
    }

    /** Called from MainVerticle. Registers all audio routes on the shared router. */
    public void register(Router router) {
        // Capture Vert.x from the router for streaming
        this.vertx = router.router() != null
                ? null   // placeholder — set via setVertx() from MainVerticle
                : null;

        // ── Existing route (PRESERVED exactly) ───────────────────────────
        router.post("/generate/audio").handler(this::generateAudio);

        // FINAL POLISH: streaming endpoint — new, additive only
        router.get("/audio/stream/:file").handler(this::streamAudio);

        LOG.info("AudioController registered: POST /generate/audio | GET /audio/stream/:file");
    }

    /**
     * Allow MainVerticle to inject Vert.x for streaming.
     * Called immediately after register() in MainVerticle.
     */
    public void setVertx(io.vertx.core.Vertx vertx) {
        this.vertx = vertx;
    }

    // ── POST /generate/audio (PRESERVED, fallback handling added) ─────────

    /**
     * FINAL POLISH: fallback handling
     *
     * Existing flow is untouched. On failure, instead of a raw 500 string,
     * the response is now a structured JSON object the client can pattern-match.
     *
     * Three failure modes:
     *   1. Missing/blank topic or mode  → 400 validation error (unchanged)
     *   2. AudioService fails with "AI returned empty script" → heuristic-mode fallback
     *   3. AudioService fails for any other reason (TTS crash, timeout) → text-mode fallback
     */
    private void generateAudio(RoutingContext ctx) {
        long start = System.currentTimeMillis();

        JsonObject body = ctx.body().asJsonObject();
        if (body == null) {
            badRequest(ctx, "Request body must be JSON");
            return;
        }

        String topic = body.getString("topic", "").trim();
        String mode  = body.getString("mode",  "single").trim();

        if (topic.isBlank()) {
            badRequest(ctx, "Field 'topic' is required");
            return;
        }

        LOG.info("POST /generate/audio topic={} mode={}", topic, mode);

        audioService.generate(topic, mode)
                .onSuccess(result -> {
                    long elapsed = System.currentTimeMillis() - start;
                    MetricsRegistry.get().recordRequest("/generate/audio", elapsed, false);
                    if (elapsed > AppConfig.SLOW_REQUEST_THRESHOLD_MS) {
                        MetricsRegistry.get().recordSlowRequest("/generate/audio", elapsed);
                        LOG.warn("SLOW AUDIO REQUEST topic={} elapsed={}ms", topic, elapsed);
                    }
                    LOG.info("AUDIO OK topic={} mode={} elapsed={}ms", topic, mode, elapsed);
                    ok(ctx, result);
                })
                .onFailure(err -> {
                    long elapsed = System.currentTimeMillis() - start;
                    MetricsRegistry.get().recordRequest("/generate/audio", elapsed, true);
                    String msg = err.getMessage() != null ? err.getMessage() : "unknown error";
                    LOG.error("AUDIO FAIL topic={} mode={} elapsed={}ms: {}", topic, mode, elapsed, msg);

                    // FINAL POLISH: fallback handling — structured error JSON
                    if (msg.contains("AI returned empty script") || msg.contains("empty script")) {
                        // AI/script generation failure → heuristic-mode fallback
                        MetricsRegistry.get().recordTtsError();
                        errorJson(ctx, 503,
                                "script_generation_failed",
                                "heuristic-mode",
                                "AI unavailable, try again shortly");
                    } else {
                        // TTS pipeline failure → text-mode fallback
                        MetricsRegistry.get().recordTtsError();
                        errorJson(ctx, 500,
                                "audio_generation_failed",
                                "text-mode",
                                "Audio unavailable, returning text response");
                    }
                });
    }

    // ── GET /audio/stream/:file ───────────────────────────────────────────

    /**
     * FINAL POLISH: streaming endpoint
     *
     * Streams a previously-generated .wav file in chunks.
     * Uses Vert.x AsyncFile + Pump — never loads the whole file into memory.
     *
     * Headers:
     *   Content-Type: audio/wav
     *   Transfer-Encoding: chunked
     *   Cache-Control: max-age=3600
     *
     * Security: only files inside AUDIO_OUTPUT_DIR are served.
     * Traversal attempts (../../etc/passwd) return 400.
     *
     * Does NOT modify POST /generate/audio.
     */
    private void streamAudio(RoutingContext ctx) {
        String filename = ctx.pathParam("file");

        // FINAL POLISH: streaming endpoint — basic path-traversal guard
        if (filename == null || filename.contains("..") || filename.contains("/")) {
            ctx.response().setStatusCode(400).end("Invalid filename");
            return;
        }

        // Only .wav files served from this endpoint
        if (!filename.endsWith(".wav")) {
            ctx.response().setStatusCode(415)
               .putHeader("Content-Type", "application/json")
               .end(new JsonObject()
                       .put("error", "unsupported_format")
                       .put("message", "Only .wav files are supported via this endpoint")
                       .encodePrettily());
            return;
        }

        File audioFile = new File(AppConfig.AUDIO_OUTPUT_DIR, filename);
        if (!audioFile.exists()) {
            ctx.response().setStatusCode(404)
               .putHeader("Content-Type", "application/json")
               .end(new JsonObject()
                       .put("error", "file_not_found")
                       .put("message", "Audio file not found: " + filename)
                       .encodePrettily());
            return;
        }

        long fileSize = audioFile.length();
        LOG.info("GET /audio/stream/{} size={}KB", filename, fileSize / 1024);

        if (vertx == null) {
            // Fallback: redirect to static handler if Vert.x not injected
            ctx.response().setStatusCode(302)
               .putHeader("Location", AppConfig.AUDIO_URL_PREFIX + filename)
               .end();
            return;
        }

        // FINAL POLISH: streaming endpoint — chunked WAV streaming via AsyncFile
        vertx.fileSystem().open(audioFile.getAbsolutePath(), new OpenOptions().setRead(true), ar -> {
            if (ar.failed()) {
                LOG.error("STREAM OPEN FAIL {}: {}", filename, ar.cause().getMessage());
                ctx.response().setStatusCode(500)
                   .putHeader("Content-Type", "application/json")
                   .end(new JsonObject()
                           .put("error", "stream_open_failed")
                           .put("message", "Could not open audio file for streaming")
                           .encodePrettily());
                return;
            }

            AsyncFile asyncFile = ar.result();

            // FINAL POLISH: streaming endpoint — set headers before first chunk
            ctx.response()
               .setChunked(true)
               .putHeader("Content-Type", "audio/wav")
               .putHeader("Cache-Control", "max-age=3600")
               .putHeader("X-Audio-File", filename)
               .putHeader("X-Audio-Size", String.valueOf(fileSize));

            // Pump: asyncFile → response — non-blocking, event-loop-safe
            io.vertx.core.streams.Pump pump = io.vertx.core.streams.Pump.pump(asyncFile, ctx.response());

            asyncFile.exceptionHandler(e -> {
                LOG.error("STREAM READ ERROR {}: {}", filename, e.getMessage());
                asyncFile.close();
                // Response may already be partially written — best-effort end
                if (!ctx.response().ended()) ctx.response().end();
            });

            ctx.response().exceptionHandler(e -> {
                LOG.warn("STREAM CLIENT DISCONNECT {}: {}", filename, e.getMessage());
                asyncFile.close();
            });

            asyncFile.endHandler(v -> {
                asyncFile.close();
                if (!ctx.response().ended()) ctx.response().end();
                LOG.debug("STREAM COMPLETE {}", filename);
            });

            pump.start();
        });
    }

    // ── Response helpers ──────────────────────────────────────────────────

    private void ok(RoutingContext ctx, JsonObject body) {
        ctx.response()
           .setStatusCode(200)
           .putHeader("Content-Type", "application/json")
           .end(body.encodePrettily());
    }

    private void badRequest(RoutingContext ctx, String message) {
        ctx.response()
           .setStatusCode(400)
           .putHeader("Content-Type", "application/json")
           .end(new JsonObject().put("error", message).encodePrettily());
    }

    /**
     * FINAL POLISH: fallback handling — emit structured error JSON.
     *
     * Format matches the spec exactly:
     *   { "error": "<code>", "fallback": "<mode>", "message": "<human text>" }
     */
    private void errorJson(RoutingContext ctx, int status, String error, String fallback, String message) {
        ctx.response()
           .setStatusCode(status)
           .putHeader("Content-Type", "application/json")
           .end(new JsonObject()
                   .put("error",    error)
                   .put("fallback", fallback)
                   .put("message",  message)
                   .encodePrettily());
    }
}
