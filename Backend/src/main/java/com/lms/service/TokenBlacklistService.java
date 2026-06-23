package com.lms.service;

import com.lms.model.BlacklistedToken;
import com.lms.repository.BlacklistedTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MongoDB-backed JWT token blacklist with automatic TTL expiration.
 *
 * When a user logs out, the JTI of their token is saved here.
 * JwtRequestFilter checks this before trusting a token.
 *
 * Fallback to per-JVM ConcurrentHashMap is implemented to support offline tests.
 */
@Service
public class TokenBlacklistService {

    private static final Logger LOG = LoggerFactory.getLogger(TokenBlacklistService.class);

    private final BlacklistedTokenRepository blacklistedTokenRepository;
    private final ConcurrentHashMap<String, Instant> localBlacklist = new ConcurrentHashMap<>();

    // Constructor with optional autowiring to support JUnit instantiation
    @Autowired
    public TokenBlacklistService(@Autowired(required = false) BlacklistedTokenRepository blacklistedTokenRepository) {
        this.blacklistedTokenRepository = blacklistedTokenRepository;
    }

    // Default constructor for compatibility
    public TokenBlacklistService() {
        this.blacklistedTokenRepository = null;
    }

    /**
     * Add a token JTI to the blacklist.
     *
     * @param jti       The JWT ID claim value.
     * @param expiry    The exact expiry instant from the token's "exp" claim.
     */
    public void blacklist(String jti, Instant expiry) {
        if (jti == null || jti.isBlank()) {
            LOG.warn("Attempted to blacklist a null/blank JTI — skipping.");
            return;
        }

        if (blacklistedTokenRepository != null) {
            try {
                blacklistedTokenRepository.save(new BlacklistedToken(jti, Date.from(expiry)));
                LOG.info("Token blacklisted in Mongo: jti={} expires={}", jti, expiry);
                return;
            } catch (Exception e) {
                LOG.error("Failed to save blacklisted token in Mongo, falling back to memory.", e);
            }
        }

        localBlacklist.put(jti, expiry);
        LOG.info("Token blacklisted in-memory: jti={} expires={}", jti, expiry);
    }

    /**
     * Check whether a given JTI has been blacklisted.
     *
     * @param jti  The JWT ID claim value from the incoming token.
     * @return     true if the token should be rejected.
     */
    public boolean isBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) return false;

        if (blacklistedTokenRepository != null) {
            try {
                return blacklistedTokenRepository.existsById(jti) || localBlacklist.containsKey(jti);
            } catch (Exception e) {
                LOG.error("Failed to query revoked token from Mongo, checking memory.", e);
            }
        }

        return localBlacklist.containsKey(jti);
    }

    /**
     * Purge entries whose token expiry is in the past.
     * MongoDB does this automatically via its TTL index, but manually purging
     * is supported for tests or backup.
     */
    @Scheduled(fixedDelay = 30 * 60 * 1_000L)
    public void purgeExpiredEntries() {
        Date now = new Date();
        long deletedFromMongo = 0;
        if (blacklistedTokenRepository != null) {
            try {
                deletedFromMongo = blacklistedTokenRepository.deleteByExpiryDateBefore(now);
            } catch (Exception e) {
                LOG.error("Failed to purge expired tokens from Mongo", e);
            }
        }

        long before = localBlacklist.size();
        localBlacklist.entrySet().removeIf(entry -> entry.getValue().isBefore(now.toInstant()));
        long removedFromMemory = before - localBlacklist.size();

        if (deletedFromMongo > 0 || removedFromMemory > 0) {
            LOG.info("TokenBlacklist purged: dbCount={}, memoryCount={}. Current memory size={}",
                    deletedFromMongo, removedFromMemory, localBlacklist.size());
        }
    }

    /** Returns the number of currently blacklisted token JTIs. */
    public int size() {
        int memorySize = localBlacklist.size();
        if (blacklistedTokenRepository != null) {
            try {
                return (int) blacklistedTokenRepository.count() + memorySize;
            } catch (Exception e) {
                LOG.error("Failed to retrieve token count from Mongo", e);
            }
        }
        return memorySize;
    }
}
