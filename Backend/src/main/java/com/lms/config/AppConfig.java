package com.lms.config;

/**
 * Application-wide constants.
 *
 * Static values are preserved exactly from the original AppConfig.
 * Runtime-configurable values (ports, URIs, thresholds) are now also
 * available via application.properties (see AppProperties), but these
 * constants remain so all existing service code compiles without change.
 */
public final class AppConfig {

    private AppConfig() {}

    // ── Server ────────────────────────────────────────────────────────────
    public static final int    HTTP_PORT                    = 8080;

    // ── MongoDB ───────────────────────────────────────────────────────────
    public static final String MONGO_CONNECTION             = "mongodb://localhost:27017";
    public static final String MONGO_DB_NAME               = "adaptive_learning";

    public static final String COL_SESSIONS                = "sessions";
    public static final String COL_QUESTIONS               = "questions";
    public static final String COL_RESPONSES               = "responses";
    public static final String COL_TOPIC_MASTERY           = "topic_mastery";

    // ── AI / Mistral ───────────────────────────────────────────────────────
    public static final String MISTRAL_API_URL              = "http://localhost:11434/v1/chat/completions";
    public static final String MISTRAL_MODEL                = "mistral";
    public static final int    AI_TIMEOUT_MS                = 300_000;
    public static final int    AI_CONCURRENCY               = 3;
    public static final int    AI_MAX_RESPONSE_CHARS        = 500;
    public static final int    AI_MAX_RETRIES               = 2;

    // ── Redis ─────────────────────────────────────────────────────────────
    public static final String REDIS_HOST                   = "localhost";
    public static final int    REDIS_PORT                   = 6379;
    public static final String REDIS_URI                    = "redis://" + REDIS_HOST + ":" + REDIS_PORT;
    public static final long   REDIS_CACHE_TTL_SECONDS      = 600L;
    public static final long   REDIS_AI_EVAL_TTL_SECONDS    = 3_600L;

    // ── In-memory fallback ────────────────────────────────────────────────
    public static final long   CACHE_TTL_MS                = 10 * 60 * 1_000L;
    public static final long   CACHE_CLEANUP_MS            = 5  * 60 * 1_000L;

    // ── Rate limiting ─────────────────────────────────────────────────────
    public static final int    RATE_LIMIT_MAX              = 5;
    public static final long   RATE_LIMIT_WINDOW_MS        = 60_000L;

    // ── Adaptive logic ────────────────────────────────────────────────────
    public static final int    STARTING_LEVEL              = 2;
    public static final int    WEAK_TOPIC_LEVEL            = 2;
    public static final double WEAK_STUDENT_ACC            = 50.0;
    public static final int    CORRECT_STREAK_TO_LEVEL     = 2;

    // ── Confidence model ──────────────────────────────────────────────────
    public static final int    MASTERY_LOG_NORM_MAX        = 100;

    // ── Trend engine ──────────────────────────────────────────────────────
    public static final double MASTERY_TREND_THRESHOLD     = 0.05;

    // ── Topic mastery tuning ──────────────────────────────────────────────
    public static final double MASTERY_EASY_THRESHOLD      = 0.40;
    public static final double MASTERY_HARD_THRESHOLD      = 0.70;
    public static final int    MASTERY_WINDOW_SIZE         = 5;
    public static final double MASTERY_WEAK_CONFIDENCE     = 0.65;

    // ── Actionable metrics ────────────────────────────────────────────────
    public static final long   SLOW_REQUEST_THRESHOLD_MS   = 2_000L;
    public static final int    METRICS_ROLLING_WINDOW      = 20;

    // ── System feedback loop bypass thresholds ────────────────────────────
    public static final double AI_FEEDBACK_BYPASS_ERROR_RATE      = 0.10;
    public static final double AI_FEEDBACK_BYPASS_MAX_TOLERANCE   = 0.15;
    public static final double AI_FEEDBACK_BYPASS_HIGH_LOAD_RPM   = 200.0;

    // ── Analytics / trend ─────────────────────────────────────────────────
    public static final int    TREND_WINDOW_SIZE           = 5;

    // ── Metrics ───────────────────────────────────────────────────────────
    public static final boolean METRICS_ENABLED            = true;
}
