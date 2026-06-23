package com.lms.util;

import com.lms.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * JWT token utility.
 *
 * Production hardening applied:
 *   - Secret loaded from AppProperties (application.properties / env var)
 *     rather than a hardcoded string literal.
 *   - Fails fast at startup if secret is blank or too short.
 *   - JTI (JWT ID) claim added to every token to support revocation.
 *   - Separate access token (10h) and refresh token (30d) generation.
 *   - Token type claim ("ACCESS" | "REFRESH") prevents cross-use.
 */
@Component
public class JwtTokenUtil {

    private static final Logger LOG = LoggerFactory.getLogger(JwtTokenUtil.class);

    private static final String CLAIM_TOKEN_TYPE = "tokenType";
    private static final String TOKEN_TYPE_ACCESS  = "ACCESS";
    private static final String TOKEN_TYPE_REFRESH = "REFRESH";

    private final AppProperties appProperties;
    private SecretKey key;

    @Autowired
    public JwtTokenUtil(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    /** Validate secret at startup — fail fast rather than silently use a weak key. */
    @PostConstruct
    public void init() {
        String secret = appProperties.getJwt().getSecret();
        if (!StringUtils.hasText(secret) || secret.length() < 32) {
            throw new IllegalStateException(
                "[SECURITY] app.jwt.secret is blank or shorter than 32 characters. " +
                "Set a strong secret via the ABF_JWT_SECRET environment variable or application.properties."
            );
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        LOG.info("JwtTokenUtil initialised — key length={}chars", secret.length());
    }

    // ── Token generation ──────────────────────────────────────────────────

    /** Generate a short-lived access token (default 10 hours). */
    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS);
        return doGenerateToken(claims, userDetails.getUsername(),
                appProperties.getJwt().getAccessTokenValidityMs());
    }

    /** Generate a long-lived refresh token (default 30 days). */
    public String generateRefreshToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH);
        return doGenerateToken(claims, userDetails.getUsername(),
                appProperties.getJwt().getRefreshTokenValidityMs());
    }

    private String doGenerateToken(Map<String, Object> claims, String subject, long validityMs) {
        String jti = UUID.randomUUID().toString();
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .id(jti)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + validityMs))
                .signWith(key)
                .compact();
    }

    // ── Token extraction ──────────────────────────────────────────────────

    public String getUsernameFromToken(String token) {
        return getClaimFromToken(token, Claims::getSubject);
    }

    public String getJtiFromToken(String token) {
        return getClaimFromToken(token, Claims::getId);
    }

    public Date getExpirationDateFromToken(String token) {
        return getClaimFromToken(token, Claims::getExpiration);
    }

    public String getTokenType(String token) {
        return getClaimFromToken(token, claims -> claims.get(CLAIM_TOKEN_TYPE, String.class));
    }

    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }

    private Claims getAllClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // ── Token validation ──────────────────────────────────────────────────

    /** Validate access token (type must be ACCESS, not expired, username matches). */
    public Boolean validateToken(String token, UserDetails userDetails) {
        try {
            final String username  = getUsernameFromToken(token);
            final String tokenType = getTokenType(token);
            return username.equals(userDetails.getUsername())
                    && TOKEN_TYPE_ACCESS.equals(tokenType)
                    && !isTokenExpired(token);
        } catch (Exception e) {
            LOG.warn("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    /** Validate refresh token (type must be REFRESH, not expired). */
    public Boolean validateRefreshToken(String token, UserDetails userDetails) {
        try {
            final String username  = getUsernameFromToken(token);
            final String tokenType = getTokenType(token);
            return username.equals(userDetails.getUsername())
                    && TOKEN_TYPE_REFRESH.equals(tokenType)
                    && !isTokenExpired(token);
        } catch (Exception e) {
            LOG.warn("Refresh token validation failed: {}", e.getMessage());
            return false;
        }
    }

    private Boolean isTokenExpired(String token) {
        final Date expiration = getExpirationDateFromToken(token);
        return expiration.before(new Date());
    }
}
