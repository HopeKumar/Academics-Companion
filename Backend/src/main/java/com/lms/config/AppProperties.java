package com.lms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Type-safe configuration properties — single source of truth for all
 * runtime-configurable settings. Bound from application.properties prefix "app".
 *
 * Usage: inject AppProperties wherever previously AppConfig static constants were used.
 */
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Ai      ai      = new Ai();
    private final Jwt     jwt     = new Jwt();
    private final Cache   cache   = new Cache();
    private final Cors    cors    = new Cors();
    private final Metrics metrics = new Metrics();
    private final Adaptive adaptive = new Adaptive();
    private final Kokoro   kokoro   = new Kokoro();

    // ── Getters ───────────────────────────────────────────────────────────

    public Ai      getAi()       { return ai; }
    public Jwt     getJwt()      { return jwt; }
    public Cache   getCache()    { return cache; }
    public Cors    getCors()     { return cors; }
    public Metrics getMetrics()  { return metrics; }
    public Adaptive getAdaptive(){ return adaptive; }
    public Kokoro  getKokoro()   { return kokoro; }

    // ── Nested sections ───────────────────────────────────────────────────

    public static class Ai {
        private String  model           = "mistral";
        private int     timeoutMs       = 60_000;
        private int     concurrency     = 3;
        private int     maxResponseChars = 500;
        private int     maxRetries       = 2;
        private boolean fastMode         = false;
        private String  fastModel        = "phi3.5";
        private String  productionModel  = "mistral";

        public String  getModel()            { return model; }
        public void    setModel(String v)    { this.model = v; }
        public int     getTimeoutMs()        { return timeoutMs; }
        public void    setTimeoutMs(int v)   { this.timeoutMs = v; }
        public int     getConcurrency()      { return concurrency; }
        public void    setConcurrency(int v) { this.concurrency = v; }
        public int     getMaxResponseChars() { return maxResponseChars; }
        public void    setMaxResponseChars(int v) { this.maxResponseChars = v; }
        public int     getMaxRetries()       { return maxRetries; }
        public void    setMaxRetries(int v)  { this.maxRetries = v; }
        public boolean isFastMode()          { return fastMode; }
        public void    setFastMode(boolean v) { this.fastMode = v; }
        public String  getFastModel()        { return fastModel; }
        public void    setFastModel(String v){ this.fastModel = v; }
        public String  getProductionModel()  { return productionModel; }
        public void    setProductionModel(String v) { this.productionModel = v; }
    }

    public static class Jwt {
        /**
         * REQUIRED — set via environment variable ABF_JWT_SECRET or application.properties.
         * Must be at least 32 characters. Application will fail to start if blank.
         */
        private String  secret                = "";
        private long    accessTokenValidityMs = 10L * 60 * 60 * 1_000; // 10 hours
        private long    refreshTokenValidityMs = 30L * 24 * 60 * 60 * 1_000; // 30 days

        public String  getSecret()                      { return secret; }
        public void    setSecret(String v)              { this.secret = v; }
        public long    getAccessTokenValidityMs()       { return accessTokenValidityMs; }
        public void    setAccessTokenValidityMs(long v) { this.accessTokenValidityMs = v; }
        public long    getRefreshTokenValidityMs()      { return refreshTokenValidityMs; }
        public void    setRefreshTokenValidityMs(long v){ this.refreshTokenValidityMs = v; }
    }

    public static class Cache {
        private long ttlSeconds          = 600;
        private long aiEvalTtlSeconds    = 3_600;

        public long  getTtlSeconds()           { return ttlSeconds; }
        public void  setTtlSeconds(long v)     { this.ttlSeconds = v; }
        public long  getAiEvalTtlSeconds()     { return aiEvalTtlSeconds; }
        public void  setAiEvalTtlSeconds(long v){ this.aiEvalTtlSeconds = v; }
    }

    public static class Cors {
        private String allowedOrigins = "http://localhost:8080";

        public String getAllowedOrigins()        { return allowedOrigins; }
        public void   setAllowedOrigins(String v){ this.allowedOrigins = v; }
    }

    public static class Metrics {
        private boolean enabled                = true;
        private long    slowRequestThresholdMs = 2_000;

        public boolean isEnabled()                     { return enabled; }
        public void    setEnabled(boolean v)           { this.enabled = v; }
        public long    getSlowRequestThresholdMs()     { return slowRequestThresholdMs; }
        public void    setSlowRequestThresholdMs(long v){ this.slowRequestThresholdMs = v; }
    }

    public static class Adaptive {
        private int    startingLevel          = 2;
        private int    weakTopicLevel         = 2;
        private double weakStudentAccuracy    = 50.0;
        private int    correctStreakToLevel   = 2;

        public int    getStartingLevel()             { return startingLevel; }
        public void   setStartingLevel(int v)        { this.startingLevel = v; }
        public int    getWeakTopicLevel()            { return weakTopicLevel; }
        public void   setWeakTopicLevel(int v)       { this.weakTopicLevel = v; }
        public double getWeakStudentAccuracy()       { return weakStudentAccuracy; }
        public void   setWeakStudentAccuracy(double v){ this.weakStudentAccuracy = v; }
        public int    getCorrectStreakToLevel()       { return correctStreakToLevel; }
        public void   setCorrectStreakToLevel(int v) { this.correctStreakToLevel = v; }
    }

    public static class Kokoro {
        private String pythonPath = "python";
        private String scriptPath = "kokoro_tts.py";
        private int    timeoutSec = 120;

        public String getPythonPath() { return pythonPath; }
        public void setPythonPath(String pythonPath) { this.pythonPath = pythonPath; }
        public String getScriptPath() { return scriptPath; }
        public void setScriptPath(String scriptPath) { this.scriptPath = scriptPath; }
        public int getTimeoutSec() { return timeoutSec; }
        public void setTimeoutSec(int timeoutSec) { this.timeoutSec = timeoutSec; }
    }
}
