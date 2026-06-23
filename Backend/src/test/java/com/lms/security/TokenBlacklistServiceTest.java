package com.lms.security;

import com.lms.service.TokenBlacklistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Security-focused tests for token blacklisting and session revocation.
 * Pure unit tests — no Spring context needed.
 */
@DisplayName("Security — Token Blacklist & Session Revocation")
class TokenBlacklistServiceTest {

    private TokenBlacklistService service;

    @BeforeEach
    void setUp() {
        service = new TokenBlacklistService();
    }

    @Test
    @DisplayName("Newly created service has empty blacklist")
    void newServiceEmptyBlacklist() {
        assertThat(service.size()).isEqualTo(0);
        assertThat(service.isBlacklisted("any-jti")).isFalse();
    }

    @Test
    @DisplayName("Blacklisted JTI is detected as blacklisted")
    void blacklistedJtiDetected() {
        service.blacklist("jti-abc", Instant.now().plusSeconds(3600));
        assertThat(service.isBlacklisted("jti-abc")).isTrue();
    }

    @Test
    @DisplayName("Non-blacklisted JTI is not detected as blacklisted")
    void nonBlacklistedJtiNotDetected() {
        service.blacklist("jti-abc", Instant.now().plusSeconds(3600));
        assertThat(service.isBlacklisted("jti-xyz")).isFalse();
    }

    @Test
    @DisplayName("Null JTI is safely handled — returns false")
    void nullJtiReturnsFalse() {
        assertThat(service.isBlacklisted(null)).isFalse();
    }

    @Test
    @DisplayName("Blank JTI is safely handled — returns false")
    void blankJtiReturnsFalse() {
        assertThat(service.isBlacklisted("")).isFalse();
        assertThat(service.isBlacklisted("   ")).isFalse();
    }

    @Test
    @DisplayName("Attempting to blacklist null JTI is silently ignored")
    void blacklistNullJtiIgnored() {
        service.blacklist(null, Instant.now().plusSeconds(3600));
        assertThat(service.size()).isEqualTo(0);
    }

    @Test
    @DisplayName("Attempting to blacklist blank JTI is silently ignored")
    void blacklistBlankJtiIgnored() {
        service.blacklist("", Instant.now().plusSeconds(3600));
        assertThat(service.size()).isEqualTo(0);
    }

    @Test
    @DisplayName("purgeExpiredEntries removes tokens that have expired")
    void purgeRemovesExpiredEntries() {
        // Add one already-expired token (expiry in the past)
        service.blacklist("expired-jti", Instant.now().minusSeconds(1));
        // Add one still-valid token
        service.blacklist("valid-jti", Instant.now().plusSeconds(3600));

        assertThat(service.size()).isEqualTo(2);

        service.purgeExpiredEntries();

        assertThat(service.size()).isEqualTo(1);
        assertThat(service.isBlacklisted("expired-jti")).isFalse();
        assertThat(service.isBlacklisted("valid-jti")).isTrue();
    }

    @Test
    @DisplayName("Multiple tokens can be blacklisted independently")
    void multipleTokensBlacklisted() {
        service.blacklist("jti-1", Instant.now().plusSeconds(3600));
        service.blacklist("jti-2", Instant.now().plusSeconds(3600));
        service.blacklist("jti-3", Instant.now().plusSeconds(3600));

        assertThat(service.size()).isEqualTo(3);
        assertThat(service.isBlacklisted("jti-1")).isTrue();
        assertThat(service.isBlacklisted("jti-2")).isTrue();
        assertThat(service.isBlacklisted("jti-3")).isTrue();
    }

    @Test
    @DisplayName("Blacklisting same JTI twice is idempotent")
    void blacklistSameJtiTwiceIdempotent() {
        Instant expiry = Instant.now().plusSeconds(3600);
        service.blacklist("jti-dup", expiry);
        service.blacklist("jti-dup", expiry);

        // ConcurrentHashMap.put on same key just updates — size stays 1
        assertThat(service.size()).isEqualTo(1);
        assertThat(service.isBlacklisted("jti-dup")).isTrue();
    }
}
