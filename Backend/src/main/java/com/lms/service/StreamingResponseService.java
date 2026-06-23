package com.lms.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Converts a reactive Flux<String> token stream into a Spring MVC SseEmitter.
 *
 * Production hardening applied:
 *   - emitter.onTimeout() → completes cleanly instead of hanging.
 *   - emitter.onCompletion() → disposes the Flux subscription to free resources.
 *   - emitter.onError() → logs the error; subscription is already disposed.
 *   - Subscription reference held so onCompletion can cancel it if client disconnects.
 *   - Token count and generation time tracked in Micrometer.
 */
@Service
public class StreamingResponseService {

    private static final Logger LOG = LoggerFactory.getLogger(StreamingResponseService.class);

    /** SSE connection timeout — 90 seconds. After this the emitter sends a timeout event. */
    private static final long SSE_TIMEOUT_MS = 90_000L;

    private final MeterRegistry meterRegistry;
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();

    public StreamingResponseService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Stream AI-generated tokens to the client via Server-Sent Events.
     *
     * @param tokenStream        Reactive Flux emitting token strings from the LLM.
     * @param onTextAccumulated  Callback invoked with the full accumulated text on completion.
     * @param onComplete         Callback invoked once on successful stream completion.
     * @return A configured SseEmitter ready to be returned from a controller.
     */
    public SseEmitter stream(Flux<String> tokenStream,
                             Consumer<String> onTextAccumulated,
                             Runnable onComplete) {

        SseEmitter     emitter     = new SseEmitter(SSE_TIMEOUT_MS);
        StringBuilder  accumulator = new StringBuilder();
        Timer.Sample   sample      = Timer.start(meterRegistry);
        AtomicInteger  tokenCount  = new AtomicInteger(0);

        // ── Subscription reference for cancellation ────────────────────────
        // We subscribe FIRST, then set up lifecycle callbacks using the reference.
        // Note: subscription is set asynchronously; we use an array trick to capture it.
        final Disposable[] subscriptionRef = new Disposable[1];

        // ── Heartbeat ──────────────────────────────────────────────────────
        ScheduledFuture<?> heartbeatTask = heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().name("ping").data("heartbeat"));
            } catch (Exception e) {
                // Ignore, handled by onError or onCompletion
            }
        }, 15, 15, TimeUnit.SECONDS);

        // ── Lifecycle hooks ────────────────────────────────────────────────

        emitter.onTimeout(() -> {
            LOG.warn("SSE stream timed out after {}ms", SSE_TIMEOUT_MS);
            meterRegistry.counter("ai.stream.timeouts").increment();
            try {
                emitter.send(SseEmitter.event().name("timeout").data("[TIMEOUT]"));
            } catch (Exception ignored) {}
            emitter.complete();
        });

        emitter.onCompletion(() -> {
            heartbeatTask.cancel(true);
            // Client disconnected or stream finished — dispose Flux subscription to stop Mistral call
            Disposable sub = subscriptionRef[0];
            if (sub != null && !sub.isDisposed()) {
                LOG.debug("SSE emitter completed — disposing token stream subscription.");
                sub.dispose();
            }
        });

        emitter.onError(e -> {
            heartbeatTask.cancel(true);
            LOG.error("SSE emitter error: {}", e.getMessage());
            meterRegistry.counter("ai.stream.errors").increment();
        });

        // ── Subscribe ──────────────────────────────────────────────────────

        subscriptionRef[0] = tokenStream.subscribe(
            // onNext: send each token
            token -> {
                try {
                    accumulator.append(token);
                    tokenCount.incrementAndGet();
                    // Escape newlines so SSE payload formatting is preserved
                    String safeToken = token.replace("\n", "\\n").replace("\r", "\\r");
                    LOG.debug("STREAM_TOKEN_SENT: {}", safeToken);
                    emitter.send(SseEmitter.event().name("token").data(safeToken));
                } catch (Exception e) {
                    // Client already disconnected — stop silently
                    LOG.debug("Client disconnected during SSE stream: {}", e.getMessage());
                    if (subscriptionRef[0] != null) subscriptionRef[0].dispose();
                }
            },
            error -> {
                LOG.error("Token stream error during generation", error);
                sample.stop(meterRegistry.timer("ai.generation.time", "status", "error"));
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data("Generation error: " + error.getMessage()));
                } catch (Exception ignored) {}
                emitter.complete();
            },
            // onComplete: fire callbacks, send [DONE], close
            () -> {
                LOG.debug("Token stream completed: tokens={}", tokenCount.get());
                sample.stop(meterRegistry.timer("ai.generation.time", "status", "success"));
                meterRegistry.counter("ai.generation.tokens").increment(tokenCount.get());
                try {
                    onTextAccumulated.accept(accumulator.toString());
                    onComplete.run();
                } catch (Exception e) {
                    LOG.error("Failed to execute stream completion callbacks", e);
                }

                try {
                    emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                    emitter.complete();
                } catch (Exception e) {
                    LOG.debug("Failed to send [DONE] event: {}", e.getMessage());
                }
            }
        );

        return emitter;
    }
}
