package com.stayease.user.security;

import com.stayease.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;
    private final String testSecret = "dGVzdC1zZWNyZXQta2V5LWZvci11c2VyLXNlcnZpY2UtdGVzdHMtbXVzdC1iZS0yNTYtYml0cw==";

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(tokenProvider, "jwtSecret", testSecret);
        ReflectionTestUtils.setField(tokenProvider, "expirationSeconds", 900L);
        ReflectionTestUtils.setField(tokenProvider, "issuer", "stayease");
        tokenProvider.init();
    }

    @Test
    @DisplayName("Should generate valid token with standard claims and 15-minute expiration")
    void shouldGenerateValidTokenWithStandardClaims() {
        User user = User.builder()
                .id(101L)
                .email("jane.doe@example.com")
                .role("ROLE_USER")
                .tokenVersion(1L)
                .build();

        Instant before = Instant.now();
        String token = tokenProvider.generateToken(user);
        Instant after = Instant.now();

        assertThat(token).isNotBlank();
        assertThat(tokenProvider.validateToken(token)).isTrue();
        assertThat(tokenProvider.getUserIdFromToken(token)).isEqualTo(101L);
        assertThat(tokenProvider.getEmailFromToken(token)).isEqualTo("jane.doe@example.com");
        assertThat(tokenProvider.getRoleFromToken(token)).isEqualTo("ROLE_USER");
        assertThat(tokenProvider.getTokenVersionFromToken(token)).isEqualTo(1L);

        Claims claims = tokenProvider.getClaims(token);
        assertThat(claims.getIssuer()).isEqualTo("stayease");
        assertThat(claims.getSubject()).isEqualTo("101");

        // Verify expiration is ~900 seconds (15 minutes) from issue time
        Date expiration = claims.getExpiration();
        long diffSeconds = (expiration.getTime() - claims.getIssuedAt().getTime()) / 1000;
        assertThat(diffSeconds).isEqualTo(900L);
    }

    @Test
    @DisplayName("Should reject tampered token with invalid signature")
    void shouldRejectTamperedToken() {
        User user = User.builder()
                .id(102L)
                .email("tamper@example.com")
                .role("ROLE_USER")
                .tokenVersion(1L)
                .build();

        String token = tokenProvider.generateToken(user);
        String tamperedToken = token.substring(0, token.length() - 5) + "ABCDE";

        assertThat(tokenProvider.validateToken(tamperedToken)).isFalse();
    }

    @Test
    @DisplayName("Should reject expired token")
    void shouldRejectExpiredToken() {
        // Create an expired token manually using the same key
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(testSecret));
        Instant past = Instant.now().minusSeconds(100);

        String expiredToken = Jwts.builder()
                .issuer("stayease")
                .subject("103")
                .claim("userId", 103L)
                .claim("email", "expired@example.com")
                .claim("role", "ROLE_USER")
                .claim("tokenVersion", 1L)
                .issuedAt(Date.from(past.minusSeconds(900)))
                .expiration(Date.from(past))
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        assertThat(tokenProvider.validateToken(expiredToken)).isFalse();
    }

    @Test
    @DisplayName("Should reject token signed with a different key")
    void shouldRejectTokenSignedWithDifferentKey() {
        SecretKey differentKey = Keys.hmacShaKeyFor("different-key-different-key-different-key-32!".getBytes(StandardCharsets.UTF_8));

        String foreignToken = Jwts.builder()
                .issuer("stayease")
                .subject("104")
                .claim("userId", 104L)
                .claim("email", "foreign@example.com")
                .claim("role", "ROLE_USER")
                .claim("tokenVersion", 1L)
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(900)))
                .signWith(differentKey, Jwts.SIG.HS256)
                .compact();

        assertThat(tokenProvider.validateToken(foreignToken)).isFalse();
    }

    @Test
    @DisplayName("Should throw IllegalStateException if JWT secret is too short")
    void shouldThrowExceptionIfSecretIsTooShort() {
        JwtTokenProvider badProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(badProvider, "jwtSecret", "too-short-secret");
        ReflectionTestUtils.setField(badProvider, "expirationSeconds", 900L);
        ReflectionTestUtils.setField(badProvider, "issuer", "stayease");

        assertThatThrownBy(badProvider::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 256 bits");
    }
}
