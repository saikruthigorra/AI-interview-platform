package com.interviewiq.security;

import com.interviewiq.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies HMAC-SHA256 JWTs.
 *
 * The signing secret comes from APP_JWT_SECRET. If it isn't set we generate a
 * strong random one at boot (fine for local dev — tokens simply stop being valid
 * after a restart) and say so loudly in the log, rather than shipping a default
 * secret in the repo.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_NAME = "name";
    private static final String CLAIM_EMAIL = "email";

    private final SecretKey key;
    private final long ttlMinutes;

    public JwtService(AppProperties props) {
        String configured = props.getSecurity().getJwtSecret();
        byte[] secretBytes;
        if (configured != null && configured.getBytes(StandardCharsets.UTF_8).length >= 32) {
            secretBytes = configured.getBytes(StandardCharsets.UTF_8);
        } else {
            if (configured != null && !configured.isBlank()) {
                log.warn("APP_JWT_SECRET is shorter than 256 bits — ignoring it and generating one for this run");
            } else {
                log.warn("APP_JWT_SECRET not set — generated an ephemeral signing key. "
                        + "Tokens issued now expire at the next restart. Set APP_JWT_SECRET (32+ chars) in production.");
            }
            secretBytes = new byte[48];
            new SecureRandom().nextBytes(secretBytes);
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.ttlMinutes = Math.max(5, props.getSecurity().getTokenTtlMinutes());
    }

    public String issueToken(Long userId, String email, String name, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_NAME, name)
                .claim(CLAIM_ROLE, role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlMinutes * 60)))
                .signWith(key)
                .compact();
    }

    public Instant expiryFor(String token) {
        return parse(token).map(Claims::getExpiration).map(Date::toInstant).orElse(Instant.now());
    }

    public Optional<Claims> parse(String token) {
        try {
            return Optional.of(Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload());
        } catch (JwtException | IllegalArgumentException ex) {
            // Expired/tampered/nonsense token — all just "not authenticated".
            return Optional.empty();
        }
    }

    public static String roleOf(Claims claims) {
        return claims.get(CLAIM_ROLE, String.class);
    }

    public static String emailOf(Claims claims) {
        return claims.get(CLAIM_EMAIL, String.class);
    }

    public static String nameOf(Claims claims) {
        return claims.get(CLAIM_NAME, String.class);
    }

    /** Exposed for tests: base64 of the key, never logged in production paths. */
    String keyFingerprint() {
        return Base64.getEncoder().encodeToString(key.getEncoded()).substring(0, 8);
    }
}
