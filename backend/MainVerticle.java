package com.lms;

import io.vertx.core.*;
import io.vertx.ext.web.*;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.*;

public class MainVerticle extends AbstractVerticle {

    // ───────────────────────────────────────────────
    // IMPROVED: structured logger (replaces bare System.out.println)
    // ───────────────────────────────────────────────
    private static final Logger LOG = Logger.getLogger(MainVerticle.class.getName());

    private MongoClient mongoClient;
    private WebClient   webClient;

    // ───────────────────────────────────────────────
    // IMPROVED: thread-safe cache with TTL eviction
    //   - ConcurrentHashMap replaces raw HashMap
    //   - Each entry stores its insertion timestamp
    //   - Entries older than CACHE_TTL_MS are treated as stale
    // ───────────────────────────────────────────────
    private static final long CACHE_TTL_MS = 10 * 60 * 1000L; // 10 minutes

    private final ConcurrentHashMap<String, CacheEntry> aiCache = new ConcurrentHashMap<>();

    private static class CacheEntry {
        final JsonObject data;
        final long       createdAt;
        CacheEntry(JsonObject data) {
            this.data      = data;
            this.createdAt = System.currentTimeMillis();
        }
        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > CACHE_TTL_MS;
        }
    }

    // ───────────────────────────────────────────────
    // IMPROVED: rate limiter
    //   - Tracks per-student request counts in a 60-second window
    //   - ConcurrentHashMap + AtomicInteger for thread safety
    // ───────────────────────────────────────────────
    private static final int    RATE_LIMIT_MAX      = 5;
    private static final long   RATE_LIMIT_WINDOW_MS = 60_000L;

    private final ConcurrentHashMap<String, RateBucket> rateBuckets = new ConcurrentHashMap<>();

    private static class RateBucket {
        final AtomicInteger count     = new AtomicInteger(0);
        volatile long       windowStart = System.currentTimeMillis();

        boolean tryAcquire() {
            long now = System.currentTimeMillis();
            if (now - windowStart > RATE_LIMIT_WINDOW_MS) {
                windowStart = now;
                count.set(0);
            }
            return count.incrementAndGet() <= RATE_LIMIT_MAX;
        }
    }

    // ───────────────────────────────────────────────
    // IMPROVED: AI request queue
    //   - Semaphore limits concurrent Ollama calls to AI_CONCURRENCY
    //   - Prevents Ollama from being overloaded with parallel requests
    // ───────────────────────────────────────────────
    private static final int AI_CONCURRENCY = 3;
    private final Semaphore  aiSemaphore    = new Semaphore(AI_CONCURRENCY, true);

    // AI timeout in milliseconds
    private static final int AI_TIMEOUT_MS = 8_000;

    // ─────────────────────────────────────────────────────────────
    @Override
    public void start(Promise<Void> startPromise) {

        JsonObject config = new JsonObject()
                .put("connection_string", "mongodb://localhost:27017")
                .put("db_name", "adaptive_learning");

        mongoClient = MongoClient.createShared(vertx, config);

        // IMPROVED: WebClient created once with timeout options
        webClient = WebClient.create(vertx, new WebClientOptions()
                .setConnectTimeout(AI_TIMEOUT_MS)
                .setIdleTimeout(AI_TIMEOUT_MS));

        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        // Core APIs
        router.post("/test/start").handler(this::startTest);
        router.get("/test/question").handler(this::getQuestion);
        router.post("/test/answer").handler(this::handleAnswer);

        // Analytics APIs
        router.get("/test/progress").handler(this::getProgress);
        router.get("/profile").handler(this::getProfile);
        router.get("/weak-topics").handler(this::getWeakTopics);
        router.get("/analytics").handler(this::getAnalytics);
        router.get("/analytics/topics").handler(this::getTopicAnalytics);

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(8080, res -> {
                    if (res.succeeded()) {
                        LOG.info("🚀 Server started on port 8080");
                        startPromise.complete();
                    } else {
                        LOG.severe("❌ Server failed to start: " + res.cause().getMessage());
                        startPromise.fail(res.cause());
                    }
                });
    }

    // ───────────────────────────────────────────────
    // IMPROVED: request validation helper
    // ───────────────────────────────────────────────
    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private void badRequest(RoutingContext ctx, String message) {
        LOG.warning("Bad request [" + ctx.request().path() + "]: " + message);
        ctx.response()
                .setStatusCode(400)
                .end(new JsonObject().put("error", message).encode());
    }

    private void internalError(RoutingContext ctx, String message) {
        LOG.severe("Internal error [" + ctx.request().path() + "]: " + message);
        ctx.response()
                .setStatusCode(500)
                .end(new JsonObject().put("error", message).encode());
    }

    // ================= START TEST =================
    private void startTest(RoutingContext ctx) {
        // IMPROVED: null / blank validation
        JsonObject body = ctx.getBodyAsJson();
        if (body == null) { badRequest(ctx, "Request body is required"); return; }

        String studentId = body.getString("studentId");
        String topic     = body.getString("topic");

        if (isBlank(studentId)) { badRequest(ctx, "studentId is required"); return; }
        if (isBlank(topic))     { badRequest(ctx, "topic is required");     return; }

        LOG.info("START TEST | student=" + studentId + " topic=" + topic);

        mongoClient.removeDocuments("sessions",
                new JsonObject().put("studentId", studentId),
                r -> {
                    if (r.failed()) {
                        internalError(ctx, "Failed to clear old session");
                        return;
                    }

                    JsonObject session = new JsonObject()
                            .put("studentId", studentId)
                            .put("topic", topic)
                            .put("level", 2)
                            .put("active", true);

                    mongoClient.save("sessions", session, res -> {
                        if (res.failed()) {
                            internalError(ctx, "Failed to create session");
                            return;
                        }
                        ctx.response().end(session.encode());
                    });
                });
    }

    // ================= GET QUESTION =================
    private void getQuestion(RoutingContext ctx) {
        // IMPROVED: null / blank validation
        String studentId = ctx.request().getParam("studentId");
        if (isBlank(studentId)) { badRequest(ctx, "studentId is required"); return; }

        LOG.info("GET QUESTION | student=" + studentId);

        mongoClient.findOne("sessions",
                new JsonObject().put("studentId", studentId),
                null,
                res -> {
                    if (res.failed()) { internalError(ctx, "DB error"); return; }

                    JsonObject session = res.result();
                    if (session == null || !session.getBoolean("active", false)) {
                        ctx.response().end(new JsonObject().put("message", "Test ended").encode());
                        return;
                    }

                    int    level = session.getInteger("level");
                    String topic = session.getString("topic");

                    mongoClient.find("questions",
                            new JsonObject().put("topic", topic).put("level", level),
                            qres -> {
                                if (qres.failed()) { internalError(ctx, "DB error"); return; }

                                if (qres.result().isEmpty()) {
                                    mongoClient.updateCollection("sessions",
                                            new JsonObject().put("studentId", studentId),
                                            new JsonObject().put("$set",
                                                    new JsonObject().put("active", false)),
                                            u -> {});

                                    ctx.response().end(new JsonObject()
                                            .put("message", "Test completed")
                                            .encode());
                                    return;
                                }

                                JsonObject question = qres.result().get(0);
                                ctx.response().end(new JsonObject()
                                        .put("question", question.getString("question"))
                                        .put("level", level)
                                        .encode());
                            });
                });
    }

    // ================= HANDLE ANSWER =================
    private void handleAnswer(RoutingContext ctx) {
        // IMPROVED: null / blank validation
        JsonObject body = ctx.getBodyAsJson();
        if (body == null) { badRequest(ctx, "Request body is required"); return; }

        String studentId  = body.getString("studentId");
        String userAnswer = body.getString("answer");

        if (isBlank(studentId))  { badRequest(ctx, "studentId is required"); return; }
        if (isBlank(userAnswer)) { badRequest(ctx, "answer is required");    return; }

        // IMPROVED: rate limiting per student
        RateBucket bucket = rateBuckets.computeIfAbsent(studentId, k -> new RateBucket());
        if (!bucket.tryAcquire()) {
            LOG.warning("RATE LIMITED | student=" + studentId);
            ctx.response()
                    .setStatusCode(429)
                    .end(new JsonObject().put("error", "Too many requests. Please wait.").encode());
            return;
        }

        final String finalUserAnswer = userAnswer.toLowerCase().trim();

        LOG.info("HANDLE ANSWER | student=" + studentId);

        mongoClient.findOne("sessions",
                new JsonObject().put("studentId", studentId),
                null,
                sessionRes -> {
                    if (sessionRes.failed()) { internalError(ctx, "DB error"); return; }

                    JsonObject session = sessionRes.result();
                    if (session == null || !session.getBoolean("active", false)) {
                        ctx.response().end(new JsonObject()
                                .put("message", "Test already ended").encode());
                        return;
                    }

                    final int    level = session.getInteger("level");
                    final String topic = session.getString("topic");

                    mongoClient.find("questions",
                            new JsonObject().put("topic", topic).put("level", level),
                            qres -> {
                                if (qres.failed()) { internalError(ctx, "DB error"); return; }

                                if (qres.result().isEmpty()) {
                                    ctx.response().end(new JsonObject()
                                            .put("error", "No question found").encode());
                                    return;
                                }

                                JsonObject question           = qres.result().get(0);
                                final String finalCorrect     = question.getString("answer");
                                final String finalQuestionText = question.getString("question");

                                // 🔥 AI EVALUATION (with queue, timeout, robust parsing)
                                evaluateWithAI(finalQuestionText, finalCorrect, finalUserAnswer,
                                        (isCorrect, explanation) -> {

                                            mongoClient.save("responses",
                                                    new JsonObject()
                                                            .put("studentId", studentId)
                                                            .put("topic", topic)
                                                            .put("question", finalQuestionText)
                                                            .put("correct", isCorrect),
                                                    r -> {});

                                            if (isCorrect) {
                                                int nextLevel = level + 1;
                                                mongoClient.updateCollection("sessions",
                                                        new JsonObject().put("studentId", studentId),
                                                        new JsonObject().put("$set",
                                                                new JsonObject().put("level", nextLevel)),
                                                        u -> {});

                                                LOG.info("CORRECT | student=" + studentId + " nextLevel=" + nextLevel);
                                                ctx.response().end(new JsonObject()
                                                        .put("result", "correct")
                                                        .put("nextLevel", nextLevel)
                                                        .encode());
                                            } else {
                                                endTest(studentId);
                                                LOG.info("WRONG | student=" + studentId + " finalLevel=" + level);
                                                ctx.response().end(new JsonObject()
                                                        .put("result", "wrong")
                                                        .put("finalLevel", level)
                                                        .put("explanation", explanation)
                                                        .encode());
                                            }
                                        });
                            });
                });
    }

    private void endTest(String studentId) {
        mongoClient.updateCollection("sessions",
                new JsonObject().put("studentId", studentId),
                new JsonObject().put("$set",
                        new JsonObject().put("active", false)),
                r -> {});
    }

    // ───────────────────────────────────────────────────────────────────
    // IMPROVED: AI evaluation — robust, timeout-protected, queue-backed
    // ───────────────────────────────────────────────────────────────────
    private void evaluateWithAI(String question, String correct, String user,
                                java.util.function.BiConsumer<Boolean, String> callback) {

        // IMPROVED: deterministic cache key using trimmed, lowercased inputs
        String key = (question.trim() + "|" + user.trim()).toLowerCase();

        // IMPROVED: TTL-aware cache lookup
        CacheEntry cached = aiCache.get(key);
        if (cached != null && !cached.isExpired()) {
            LOG.fine("AI CACHE HIT | key=" + key.substring(0, Math.min(40, key.length())));
            callback.accept(
                    cached.data.getBoolean("correct"),
                    cached.data.getString("explanation"));
            return;
        }

        // Remove stale entry if present
        if (cached != null) aiCache.remove(key);

        // IMPROVED: semaphore-backed queue — blocks excess concurrent AI calls
        if (!aiSemaphore.tryAcquire()) {
            LOG.warning("AI QUEUE FULL — returning fallback");
            callback.accept(false, "Service is busy. Please try again shortly.");
            return;
        }

        LOG.info("AI CALL | question=" + question.substring(0, Math.min(60, question.length())));

        // IMPROVED: structured prompt that forces a clear first token
        String prompt = "You are a strict answer evaluator.\n\n"
                + "Question: " + question + "\n"
                + "Correct Answer: " + correct + "\n"
                + "Student Answer: " + user + "\n\n"
                + "Rules:\n"
                + "1. Your FIRST word must be exactly 'CORRECT' or 'WRONG' (uppercase, nothing before it).\n"
                + "2. Then provide a brief explanation on the next line.\n"
                + "Example response:\n"
                + "CORRECT\nThe student correctly identified the concept.\n\n"
                + "Now evaluate:";

        JsonObject request = new JsonObject()
                .put("model", "mistral")
                .put("prompt", prompt)
                .put("stream", false);

        // IMPROVED: timeout handling — vertx timer fires fallback if AI hangs
        final long[] timerId = {-1};
        final boolean[] responded = {false};

        timerId[0] = vertx.setTimer(AI_TIMEOUT_MS, t -> {
            if (!responded[0]) {
                responded[0] = true;
                aiSemaphore.release();
                LOG.warning("AI TIMEOUT after " + AI_TIMEOUT_MS + "ms");
                callback.accept(false, "Evaluation timed out. Please retry.");
            }
        });

        webClient.post(11434, "localhost", "/api/generate")
                .sendJsonObject(request, ar -> {

                    // Guard: if timeout already fired, ignore this response
                    if (responded[0]) return;
                    responded[0] = true;
                    vertx.cancelTimer(timerId[0]);
                    aiSemaphore.release();

                    if (ar.failed()) {
                        LOG.warning("AI HTTP FAILED: " + ar.cause().getMessage());
                        callback.accept(false, "AI evaluation unavailable. Please retry.");
                        return;
                    }

                    String rawResponse;
                    try {
                        rawResponse = ar.result().bodyAsJsonObject()
                                .getString("response", "").trim();
                    } catch (Exception e) {
                        LOG.warning("AI RESPONSE PARSE ERROR: " + e.getMessage());
                        callback.accept(false, "AI returned an unreadable response.");
                        return;
                    }

                    // ───────────────────────────────────────────────
                    // IMPROVED: robust evaluation parsing
                    //   Old logic: response.contains("correct") && !response.contains("wrong")
                    //   Problem:   "This is NOT correct" → false positive
                    //              "wrong because not correct" → false negative
                    //   New logic: check the FIRST word only (case-insensitive)
                    //              fallback to keyword scan only if first-word is ambiguous
                    // ───────────────────────────────────────────────
                    boolean isCorrect = parseAiVerdict(rawResponse);
                    String  explanation = rawResponse.length() > 500
                            ? rawResponse.substring(0, 500) + "..."
                            : rawResponse;

                    LOG.info("AI RESULT | correct=" + isCorrect);

                    // Cache the result
                    aiCache.put(key, new CacheEntry(new JsonObject()
                            .put("correct", isCorrect)
                            .put("explanation", explanation)));

                    callback.accept(isCorrect, explanation);
                });
    }

    /**
     * IMPROVED: safe AI verdict parser.
     *
     * Strategy (in order):
     *  1. Check if the first word is "correct" or "wrong" — most reliable signal.
     *  2. If ambiguous, fall back to a balanced keyword search with negation awareness.
     *  3. Default to false (conservative — never give credit on ambiguity).
     */
    private boolean parseAiVerdict(String response) {
        if (response == null || response.isBlank()) return false;

        String lower = response.toLowerCase().trim();

        // Step 1: first-word check
        String firstWord = lower.split("[\\s\\n\\r.,!?]+")[0];
        if (firstWord.equals("correct"))  return true;
        if (firstWord.equals("wrong"))    return false;
        if (firstWord.equals("yes"))      return true;
        if (firstWord.equals("no"))       return false;
        if (firstWord.equals("incorrect")) return false;

        // Step 2: negation-aware keyword scan
        //   "not correct", "not right", "is incorrect" → wrong
        boolean hasCorrect = lower.contains("correct") || lower.contains("right answer")
                || lower.contains("is accurate");
        boolean hasWrong   = lower.contains("wrong") || lower.contains("incorrect")
                || lower.contains("not correct") || lower.contains("not right")
                || lower.contains("is not") || lower.contains("isn't");

        if (hasCorrect && !hasWrong) return true;
        if (hasWrong)                return false;

        // Step 3: default conservative
        LOG.warning("AMBIGUOUS AI RESPONSE — defaulting to wrong: " + lower.substring(0, Math.min(80, lower.length())));
        return false;
    }

    // ─────────────────────────────────────────────
    // IMPROVED: analytics with per-topic accuracy
    // ─────────────────────────────────────────────
    private void getAnalytics(RoutingContext ctx) {

        mongoClient.find("responses", new JsonObject(), res -> {
            if (res.failed()) { internalError(ctx, "DB error"); return; }

            Map<String, Integer> total   = new HashMap<>();
            Map<String, Integer> correct = new HashMap<>();

            for (JsonObject r : res.result()) {
                String student = r.getString("studentId");
                if (student == null) continue;

                total.put(student, total.getOrDefault(student, 0) + 1);
                if (Boolean.TRUE.equals(r.getBoolean("correct"))) {
                    correct.put(student, correct.getOrDefault(student, 0) + 1);
                }
            }

            // IMPROVED: richer analytics — accuracy per student + weak threshold configurable
            List<JsonObject> studentStats = new ArrayList<>();
            List<String>     weakStudents = new ArrayList<>();

            for (String s : total.keySet()) {
                int    t   = total.get(s);
                int    c   = correct.getOrDefault(s, 0);
                double acc = Math.round((c * 100.0 / t) * 10.0) / 10.0; // 1 decimal

                studentStats.add(new JsonObject()
                        .put("studentId", s)
                        .put("totalQuestions", t)
                        .put("correctAnswers", c)
                        .put("accuracy", acc));

                if (acc < 50.0) weakStudents.add(s);
            }

            ctx.response().end(new JsonObject()
                    .put("totalStudents", total.size())
                    .put("weakStudents", weakStudents)
                    .put("studentStats", studentStats)   // IMPROVED: added full breakdown
                    .encode());
        });
    }

    // IMPROVED: topic analytics includes attempt count + average level
    private void getTopicAnalytics(RoutingContext ctx) {

        mongoClient.find("sessions", new JsonObject(), res -> {
            if (res.failed()) { internalError(ctx, "DB error"); return; }

            Map<String, Integer> levelSum = new HashMap<>();
            Map<String, Integer> count    = new HashMap<>();

            for (JsonObject s : res.result()) {
                String topic = s.getString("topic");
                if (topic == null) continue;
                Integer level = s.getInteger("level");
                if (level == null) continue;

                levelSum.put(topic, levelSum.getOrDefault(topic, 0) + level);
                count.put(topic, count.getOrDefault(topic, 0) + 1);
            }

            // IMPROVED: return avgLevel + totalAttempts per topic (not just raw sum)
            Map<String, JsonObject> topicStats = new LinkedHashMap<>();
            for (String topic : levelSum.keySet()) {
                int    c   = count.get(topic);
                double avg = Math.round((levelSum.get(topic) * 10.0 / c)) / 10.0;
                topicStats.put(topic, new JsonObject()
                        .put("avgLevel", avg)
                        .put("totalAttempts", c));
            }

            ctx.response().end(new JsonObject()
                    .put("topicStats", topicStats)
                    .encode());
        });
    }

    private void getProgress(RoutingContext ctx) {
        String studentId = ctx.request().getParam("studentId");
        if (isBlank(studentId)) { badRequest(ctx, "studentId is required"); return; }

        mongoClient.find("responses",
                new JsonObject().put("studentId", studentId),
                res -> {
                    if (res.failed()) { internalError(ctx, "DB error"); return; }

                    List<JsonObject> list  = res.result();
                    int total   = list.size();
                    int correct = (int) list.stream()
                            .filter(r -> Boolean.TRUE.equals(r.getBoolean("correct")))
                            .count();

                    // IMPROVED: rounded accuracy
                    double accuracy = total == 0 ? 0 :
                            Math.round((correct * 100.0 / total) * 10.0) / 10.0;

                    ctx.response().end(new JsonObject()
                            .put("totalQuestions", total)
                            .put("correct", correct)
                            .put("accuracy", accuracy)
                            .encode());
                });
    }

    private void getProfile(RoutingContext ctx) {
        String studentId = ctx.request().getParam("studentId");
        if (isBlank(studentId)) { badRequest(ctx, "studentId is required"); return; }

        mongoClient.find("sessions",
                new JsonObject().put("studentId", studentId),
                res -> {
                    if (res.failed()) { internalError(ctx, "DB error"); return; }

                    JsonObject profile = new JsonObject();
                    for (JsonObject s : res.result()) {
                        String topic = s.getString("topic");
                        if (topic != null) profile.put(topic, s.getInteger("level"));
                    }
                    ctx.response().end(profile.encode());
                });
    }

    private void getWeakTopics(RoutingContext ctx) {
        String studentId = ctx.request().getParam("studentId");
        if (isBlank(studentId)) { badRequest(ctx, "studentId is required"); return; }

        mongoClient.find("sessions",
                new JsonObject().put("studentId", studentId),
                res -> {
                    if (res.failed()) { internalError(ctx, "DB error"); return; }

                    List<String> weak = res.result().stream()
                            .filter(s -> s.getInteger("level", 0) <= 2)
                            .map(s -> s.getString("topic"))
                            .filter(t -> t != null)
                            .distinct() // IMPROVED: deduplicate topics
                            .toList();

                    ctx.response().end(new JsonObject()
                            .put("weakTopics", weak)
                            .encode());
                });
    }

    public static void main(String[] args) {
        // IMPROVED: deploy with worker thread count matching CPU cores
        DeploymentOptions opts = new DeploymentOptions()
                .setInstances(Runtime.getRuntime().availableProcessors());
        Vertx.vertx().deployVerticle(MainVerticle.class.getName(), opts);
    }
}
