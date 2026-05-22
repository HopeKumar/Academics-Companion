package com.lms;

import io.vertx.core.*;
import io.vertx.ext.web.*;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.client.WebClient;

import java.util.*;

public class MainVerticle extends AbstractVerticle {

    private MongoClient mongoClient;

    // ⚡ AI CACHE
    private Map<String, JsonObject> aiCache = new HashMap<>();

    @Override
    public void start(Promise<Void> startPromise) {

        JsonObject config = new JsonObject()
                .put("connection_string", "mongodb://localhost:27017")
                .put("db_name", "adaptive_learning");

        mongoClient = MongoClient.createShared(vertx, config);

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
                        System.out.println("🚀 Server started on port 8080");
                        startPromise.complete();
                    } else {
                        startPromise.fail(res.cause());
                    }
                });
    }

    // ================= START TEST =================
    private void startTest(RoutingContext ctx) {

        JsonObject body = ctx.getBodyAsJson();

        String studentId = body.getString("studentId");
        String topic = body.getString("topic");

        mongoClient.removeDocuments("sessions",
                new JsonObject().put("studentId", studentId),
                r -> {

                    JsonObject session = new JsonObject()
                            .put("studentId", studentId)
                            .put("topic", topic)
                            .put("level", 2)
                            .put("active", true);

                    mongoClient.save("sessions", session, res -> {
                        ctx.response().end(session.encode());
                    });
                });
    }

    // ================= GET QUESTION =================
    private void getQuestion(RoutingContext ctx) {

        String studentId = ctx.request().getParam("studentId");

        mongoClient.findOne("sessions",
                new JsonObject().put("studentId", studentId),
                null,
                res -> {

                    JsonObject session = res.result();

                    if (session == null || !session.getBoolean("active", false)) {
                        ctx.response().end("Test ended");
                        return;
                    }

                    int level = session.getInteger("level");
                    String topic = session.getString("topic");

                    mongoClient.find("questions",
                            new JsonObject().put("topic", topic).put("level", level),
                            qres -> {

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

        JsonObject body = ctx.getBodyAsJson();

        String studentId = body.getString("studentId");
        String userAnswer = body.getString("answer");

        final String finalUserAnswer = userAnswer.toLowerCase();

        mongoClient.findOne("sessions",
                new JsonObject().put("studentId", studentId),
                null,
                sessionRes -> {

                    JsonObject session = sessionRes.result();

                    if (session == null || !session.getBoolean("active", false)) {
                        ctx.response().end("Test already ended");
                        return;
                    }

                    final int level = session.getInteger("level");
                    final String topic = session.getString("topic");

                    mongoClient.find("questions",
                            new JsonObject().put("topic", topic).put("level", level),
                            qres -> {

                                if (qres.result().isEmpty()) {
                                    ctx.response().end("No question found");
                                    return;
                                }

                                JsonObject question = qres.result().get(0);

                                final String finalCorrectAnswer = question.getString("answer");
                                final String finalQuestionText = question.getString("question");

                                // 🔥 AI EVALUATION
                                evaluateWithAI(finalQuestionText, finalCorrectAnswer, finalUserAnswer,
                                        (isCorrect, explanation) -> {

                                            mongoClient.save("responses",
                                                    new JsonObject()
                                                            .put("studentId", studentId)
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

                                                ctx.response().end(new JsonObject()
                                                        .put("result", "correct")
                                                        .put("nextLevel", nextLevel)
                                                        .encode());

                                            } else {

                                                endTest(studentId);

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

    // ================= AI (FAST + CACHE) =================
    private void evaluateWithAI(String question, String correct, String user,
                                java.util.function.BiConsumer<Boolean, String> callback) {

        String key = question + "|" + user;

        if (aiCache.containsKey(key)) {
            JsonObject cached = aiCache.get(key);
            callback.accept(cached.getBoolean("correct"), cached.getString("explanation"));
            return;
        }

        WebClient client = WebClient.create(vertx);

        String prompt = "You are a strict evaluator.\n\n"
                + "Question: " + question + "\n"
                + "Correct Answer: " + correct + "\n"
                + "Student Answer: " + user + "\n\n"
                + "Reply ONLY 'correct' or 'wrong' and explain.";

        JsonObject request = new JsonObject()
                .put("model", "mistral")
                .put("prompt", prompt)
                .put("stream", false);

        client.post(11434, "localhost", "/api/generate")
                .sendJsonObject(request, ar -> {

                    if (ar.failed()) {
                        callback.accept(false, "AI failed");
                        return;
                    }

                    String response = ar.result().bodyAsJsonObject().getString("response").toLowerCase();

                    boolean isCorrect = response.contains("correct") && !response.contains("wrong");

                    aiCache.put(key, new JsonObject()
                            .put("correct", isCorrect)
                            .put("explanation", response));

                    callback.accept(isCorrect, response);
                });
    }

    // ================= ANALYTICS =================
    private void getAnalytics(RoutingContext ctx) {

        mongoClient.find("responses", new JsonObject(), res -> {

            Map<String, Integer> total = new HashMap<>();
            Map<String, Integer> correct = new HashMap<>();

            for (JsonObject r : res.result()) {

                String student = r.getString("studentId");

                total.put(student, total.getOrDefault(student, 0) + 1);

                if (r.getBoolean("correct")) {
                    correct.put(student, correct.getOrDefault(student, 0) + 1);
                }
            }

            List<String> weak = new ArrayList<>();

            for (String s : total.keySet()) {

                double acc = (correct.getOrDefault(s, 0) * 100.0) / total.get(s);

                if (acc < 50) {
                    weak.add(s);
                }
            }

            ctx.response().end(new JsonObject()
                    .put("totalStudents", total.size())
                    .put("weakStudents", weak)
                    .encode());
        });
    }

    private void getTopicAnalytics(RoutingContext ctx) {

        mongoClient.find("sessions", new JsonObject(), res -> {

            Map<String, Integer> map = new HashMap<>();

            for (JsonObject s : res.result()) {
                map.put(s.getString("topic"),
                        map.getOrDefault(s.getString("topic"), 0) + s.getInteger("level"));
            }

            ctx.response().end(new JsonObject()
                    .put("topicLevels", map)
                    .encode());
        });
    }

    private void getProgress(RoutingContext ctx) {

        String studentId = ctx.request().getParam("studentId");

        mongoClient.find("responses",
                new JsonObject().put("studentId", studentId),
                res -> {

                    List<JsonObject> list = res.result();

                    int total = list.size();
                    int correct = (int) list.stream().filter(r -> r.getBoolean("correct")).count();

                    double accuracy = total == 0 ? 0 : (correct * 100.0 / total);

                    ctx.response().end(new JsonObject()
                            .put("totalQuestions", total)
                            .put("correct", correct)
                            .put("accuracy", accuracy)
                            .encode());
                });
    }

    private void getProfile(RoutingContext ctx) {

        String studentId = ctx.request().getParam("studentId");

        mongoClient.find("sessions",
                new JsonObject().put("studentId", studentId),
                res -> {

                    JsonObject profile = new JsonObject();

                    for (JsonObject s : res.result()) {
                        profile.put(s.getString("topic"), s.getInteger("level"));
                    }

                    ctx.response().end(profile.encode());
                });
    }

    private void getWeakTopics(RoutingContext ctx) {

        String studentId = ctx.request().getParam("studentId");

        mongoClient.find("sessions",
                new JsonObject().put("studentId", studentId),
                res -> {

                    List<String> weak = res.result().stream()
                            .filter(s -> s.getInteger("level") <= 2)
                            .map(s -> s.getString("topic"))
                            .toList();

                    ctx.response().end(new JsonObject()
                            .put("weakTopics", weak)
                            .encode());
                });
    }

    public static void main(String[] args) {
        Vertx.vertx().deployVerticle(new MainVerticle());
    }
}
