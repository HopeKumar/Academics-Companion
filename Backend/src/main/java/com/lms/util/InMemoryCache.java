package com.lms.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Pure in-memory cache replacing the previous Redis + fallback implementation.
 *
 * Drop-in replacement for RedisCache:
 *   - Same public API: get(), set(), setWithTtl(), buildKey()
 *   - No external dependencies (Redis/Lettuce removed)
 *   - Thread-safe via ConcurrentHashMap
 *   - TTL enforcement on read (lazy eviction) + size-based cleanup at 5 000 entries
 *   - SHA-256 normalized key builder preserved exactly
 */
@Component
public class InMemoryCache {

    private static final Logger LOG = LoggerFactory.getLogger(InMemoryCache.class);

    private static final long DEFAULT_TTL_SECONDS    = 600L;    // 10 min
    private static final long AI_EVAL_TTL_SECONDS    = 3_600L;  // 1 h
    private static final int  MAX_ENTRIES            = 5_000;

    private final ConcurrentHashMap<String, CacheEntry> store = new ConcurrentHashMap<>();

    // ── Public API ────────────────────────────────────────────────────────

    /** Async-style get — callback receives value or null on miss/expiry. */
    public void get(String key, Consumer<String> callback) {
        CacheEntry entry = store.get(key);
        if (entry == null) {
            LOG.debug("CACHE MISS key={}", abbrev(key));
            callback.accept(null);
            return;
        }
        if (entry.isExpired()) {
            store.remove(key);
            LOG.debug("CACHE EXPIRED key={}", abbrev(key));
            callback.accept(null);
            return;
        }
        LOG.debug("CACHE HIT key={}", abbrev(key));
        callback.accept(entry.value);
    }

    /** Store with default TTL (10 min). */
    public void set(String key, String value) {
        setWithTtl(key, value, DEFAULT_TTL_SECONDS);
    }

    /** Store with explicit TTL (use AI_EVAL_TTL_SECONDS for AI results). */
    public void setWithTtl(String key, String value, long ttlSeconds) {
        if (store.size() >= MAX_ENTRIES) {
            store.entrySet().removeIf(e -> e.getValue().isExpired());
        }
        store.put(key, new CacheEntry(value, ttlSeconds));
    }

    // ── Normalized compound key builder (preserved from RedisCache) ───────

    /**
     * Builds a normalised, hashed cache key for cross-user reuse.
     *
     * Pipeline per part:
     *   1. lowercase
     *   2. strip punctuation → space
     *   3. collapse whitespace
     *   4. trim
     * Then SHA-256(namespace + "|" + part1 + "|" + ...) → 64-char hex.
     *
     * Usage: InMemoryCache.buildKey("ai", question, correctAnswer, studentAnswer)
     */
    public static String buildKey(String namespace, String... parts) {
        StringBuilder sb = new StringBuilder(namespace);
        for (String p : parts) {
            sb.append("|").append(normalise(p));
        }
        return namespace + ":" + sha256(sb.toString());
    }

    // ── Internals ─────────────────────────────────────────────────────────

    private static String normalise(String s) {
        if (s == null) return "";
        String lower     = s.toLowerCase();
        String noPunct   = lower.replaceAll("[^a-z0-9\\s]", " ");
        String collapsed = noPunct.replaceAll("\\s+", " ");
        return collapsed.trim();
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return input.length() > 64 ? input.substring(0, 64) : input;
        }
    }

    private String abbrev(String s) {
        return s.length() > 60 ? s.substring(0, 60) + "..." : s;
    }

    // ── Entry holder ──────────────────────────────────────────────────────

    private static class CacheEntry {
        final String value;
        final long   expiresAt;

        CacheEntry(String value, long ttlSeconds) {
            this.value     = value;
            this.expiresAt = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(ttlSeconds);
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}
