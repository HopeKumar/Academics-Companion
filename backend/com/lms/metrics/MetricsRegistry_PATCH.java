// ═══════════════════════════════════════════════════════════════════════════
// MetricsRegistry — FINAL POLISH additions only.
//
// Add the two blocks below into the existing MetricsRegistry.java.
// All existing fields and methods are PRESERVED exactly.
//
// Where to insert:
//   After the "FINAL: TTS latency tracking" block (after recordTtsError()),
//   before the "Global slow counter" section.
// ═══════════════════════════════════════════════════════════════════════════

    // ── FINAL POLISH: segment generation time ─────────────────────────────

    private final java.util.concurrent.atomic.AtomicLong segmentCalls   = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong segmentTotalMs = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong segmentMaxMs   = new java.util.concurrent.atomic.AtomicLong();

    /**
     * FINAL POLISH: segment generation time
     *
     * Records the wall-clock duration of one TTS segment synthesis call.
     * Called once per speaker segment inside AudioService.runPodcastTts().
     *
     * Exposed in snapshot() under "tts.segmentAvgMs" and "tts.segmentMaxMs".
     */
    public void recordSegmentLatency(long ms) {
        segmentCalls.incrementAndGet();
        segmentTotalMs.addAndGet(ms);
        segmentMaxMs.updateAndGet(current -> Math.max(current, ms));
    }

    // ── FINAL POLISH: merge time ──────────────────────────────────────────

    private final java.util.concurrent.atomic.AtomicLong mergeCalls   = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong mergeTotalMs = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong mergeMaxMs   = new java.util.concurrent.atomic.AtomicLong();

    /**
     * FINAL POLISH: merge time
     *
     * Records the wall-clock duration of the sox/ffmpeg audio merge step.
     * Called once per podcast pipeline completion in AudioService.runPodcastTts().
     *
     * Exposed in snapshot() under "tts.mergeAvgMs" and "tts.mergeMaxMs".
     */
    public void recordMergeLatency(long ms) {
        mergeCalls.incrementAndGet();
        mergeTotalMs.addAndGet(ms);
        mergeMaxMs.updateAndGet(current -> Math.max(current, ms));
    }

// ═══════════════════════════════════════════════════════════════════════════
// snapshot() — replace the existing "tts" JsonObject block inside snapshot()
// with the expanded version below.  Everything else in snapshot() is unchanged.
// ═══════════════════════════════════════════════════════════════════════════

/*  OLD block (inside snapshot() — remove this):
 *
 *      .put("tts", new JsonObject()
 *              .put("totalCalls",  ttsNet)
 *              .put("totalErrors", ttsErrors.get())
 *              .put("avgMs",       ttsNet == 0 ? 0 : ttsTotalMs.get() / ttsNet))
 *
 *  NEW block (replace with this):
 */
/*
    .put("tts", new JsonObject()
            .put("totalCalls",      ttsCalls.get())
            .put("totalErrors",     ttsErrors.get())
            .put("avgMs",           ttsCalls.get() == 0 ? 0 : ttsTotalMs.get() / ttsCalls.get())
            // FINAL POLISH: segment generation time
            .put("segmentCalls",    segmentCalls.get())
            .put("segmentAvgMs",    segmentCalls.get() == 0 ? 0 : segmentTotalMs.get() / segmentCalls.get())
            .put("segmentMaxMs",    segmentMaxMs.get())
            // FINAL POLISH: merge time
            .put("mergeCalls",      mergeCalls.get())
            .put("mergeAvgMs",      mergeCalls.get() == 0 ? 0 : mergeTotalMs.get() / mergeCalls.get())
            .put("mergeMaxMs",      mergeMaxMs.get()))
*/

// ═══════════════════════════════════════════════════════════════════════════
// summarySnapshot() — add two lines to the existing return statement.
// ═══════════════════════════════════════════════════════════════════════════

/*  Add after the existing .put("ttsAvgMs", ...) line:
 *
 *      // FINAL POLISH: segment generation time / merge time
 *      .put("segmentAvgMs",  segmentCalls.get() == 0 ? 0 : segmentTotalMs.get() / segmentCalls.get())
 *      .put("mergeAvgMs",    mergeCalls.get()   == 0 ? 0 : mergeTotalMs.get()   / mergeCalls.get())
 */
