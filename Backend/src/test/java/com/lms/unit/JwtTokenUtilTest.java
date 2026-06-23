package com.lms.unit;

import com.lms.config.AppProperties;
import com.lms.util.JwtTokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for JwtTokenUtil — token generation, validation, JTI, token types.
 * No Spring context needed; we instantiate AppProperties manually.
 */
@DisplayName("JwtTokenUtil — Token Generation & Validation")
class JwtTokenUtilTest {

    private JwtTokenUtil jwtTokenUtil;
    private UserDetails  testUser;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        // Valid 64-character secret for testing
        props.getJwt().setSecret("test-secret-key-that-is-at-least-32-characters-long-for-testing!");
        props.getJwt().setAccessTokenValidityMs(10L * 60 * 60 * 1_000);
        props.getJwt().setRefreshTokenValidityMs(30L * 24 * 60 * 60 * 1_000);

        jwtTokenUtil = new JwtTokenUtil(props);
        jwtTokenUtil.init(); // trigger @PostConstruct manually

        testUser = new User("alice", "hashed-password", Collections.emptyList());
    }

    @Test
    @DisplayName("generateToken returns a non-blank token")
    void generateTokenNotBlank() {
        String token = jwtTokenUtil.generateToken(testUser);
        assertThat(token).isNotBlank();
    }

    @Test
    @DisplayName("getUsernameFromToken returns correct username")
    void usernameExtractedCorrectly() {
        String token    = jwtTokenUtil.generateToken(testUser);
        String username = jwtTokenUtil.getUsernameFromToken(token);
        assertThat(username).isEqualTo("alice");
    }

    @Test
    @DisplayName("validateToken returns true for valid token and matching user")
    void validateTokenTrue() {
        String token = jwtTokenUtil.generateToken(testUser);
        assertThat(jwtTokenUtil.validateToken(token, testUser)).isTrue();
    }

    @Test
    @DisplayName("validateToken returns false for different user")
    void validateTokenFalseForDifferentUser() {
        String token = jwtTokenUtil.generateToken(testUser);
        UserDetails otherUser = new User("bob", "hashed", Collections.emptyList());
        assertThat(jwtTokenUtil.validateToken(token, otherUser)).isFalse();
    }

    @Test
    @DisplayName("Access token fails validateRefreshToken (type mismatch)")
    void accessTokenFailsRefreshValidation() {
        String accessToken = jwtTokenUtil.generateToken(testUser);
        assertThat(jwtTokenUtil.validateRefreshToken(accessToken, testUser)).isFalse();
    }

    @Test
    @DisplayName("Refresh token fails validateToken (type mismatch)")
    void refreshTokenFailsAccessValidation() {
        String refreshToken = jwtTokenUtil.generateRefreshToken(testUser);
        assertThat(jwtTokenUtil.validateToken(refreshToken, testUser)).isFalse();
    }

    @Test
    @DisplayName("validateRefreshToken returns true for valid refresh token")
    void validateRefreshTokenTrue() {
        String refreshToken = jwtTokenUtil.generateRefreshToken(testUser);
        assertThat(jwtTokenUtil.validateRefreshToken(refreshToken, testUser)).isTrue();
    }

    @Test
    @DisplayName("getJtiFromToken returns non-blank JTI")
    void jtiIsNonBlank() {
        String token = jwtTokenUtil.generateToken(testUser);
        String jti   = jwtTokenUtil.getJtiFromToken(token);
        assertThat(jti).isNotBlank();
    }

    @Test
    @DisplayName("Two tokens have different JTIs")
    void twoTokensHaveDifferentJtis() {
        String t1 = jwtTokenUtil.generateToken(testUser);
        String t2 = jwtTokenUtil.generateToken(testUser);
        assertThat(jwtTokenUtil.getJtiFromToken(t1))
                .isNotEqualTo(jwtTokenUtil.getJtiFromToken(t2));
    }

    @Test
    @DisplayName("init() fails fast when secret is blank")
    void failsFastWhenSecretIsBlank() {
        AppProperties badProps = new AppProperties();
        badProps.getJwt().setSecret("");
        JwtTokenUtil badUtil = new JwtTokenUtil(badProps);

        assertThatThrownBy(badUtil::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SECURITY");
    }

    @Test
    @DisplayName("init() fails fast when secret is shorter than 32 chars")
    void failsFastWhenSecretTooShort() {
        AppProperties badProps = new AppProperties();
        badProps.getJwt().setSecret("tooshort");
        JwtTokenUtil badUtil = new JwtTokenUtil(badProps);

        assertThatThrownBy(badUtil::init)
                .isInstanceOf(IllegalStateException.class);
    }
}
