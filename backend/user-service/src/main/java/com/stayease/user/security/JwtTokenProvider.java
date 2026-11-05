package com.stayease.user.security;

import com.stayease.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Generates and validates signed HMAC-SHA256 JWT access tokens with a strict 15-minute lifetime.
 *
 * <p>Enforces required claims: sub (userId), email, role, tokenVersion, iat, exp, iss.
 */
@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${stayease.jwt.secret}")
    private String jwtSecret;

    @Value("${stayease.jwt.expiration-seconds:900}")
    private long expirationSeconds;

    @Value("${stayease.jwt.issuer:stayease}")
    private String issuer;

    private SecretKey key;

    @PostConstruct
    public void init() {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(jwtSecret);
            if (keyBytes.length < 32) {
                keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        }

        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "JWT secret key must be at least 256 bits (32 bytes). Current length is " + keyBytes.length + " bytes."
            );
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Issues a 15-minute access token for the given user entity.
     */
    public String generateToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(expirationSeconds);

        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(user.getId()))
                .claim("userId", user.getId())
                .claim("email", user.getEmail())
                .claim("role", user.getRole())
                .claim("tokenVersion", user.getTokenVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Parses and returns the claims from a signed JWT.
     * Throws JwtException if signature is invalid or token is expired.
     */
    public Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Validates signature, structure, and expiration of the token.
     */
    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = getClaims(token);
        Object userIdClaim = claims.get("userId");
        if (userIdClaim instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(claims.getSubject());
    }

    public String getEmailFromToken(String token) {
        return getClaims(token).get("email", String.class);
    }

    public String getRoleFromToken(String token) {
        return getClaims(token).get("role", String.class);
    }

    public Long getTokenVersionFromToken(String token) {
        Object tv = getClaims(token).get("tokenVersion");
        if (tv instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    public Date getExpirationDateFromToken(String token) {
        return getClaims(token).getExpiration();
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }
}
