// ═══════════════════════════════════════════════════════════════════════════
// MainVerticle — FINAL POLISH: one-line addition only.
//
// After audioController.register(router), add:
//
//   audioController.setVertx(vertx);
//
// This injects the Vert.x instance needed by the streaming endpoint.
// Without it, GET /audio/stream/:file falls back to a 302 redirect
// to the static handler — still functional but not chunked streaming.
//
// Full diff shown below for clarity.
// ═══════════════════════════════════════════════════════════════════════════

/*
  BEFORE (in MainVerticle.start()):

        audioController.register(router);

  AFTER:

        // FINAL POLISH: streaming endpoint — inject Vert.x for AsyncFile pump
        audioController.register(router);
        audioController.setVertx(vertx);          // ← add this line
*/
