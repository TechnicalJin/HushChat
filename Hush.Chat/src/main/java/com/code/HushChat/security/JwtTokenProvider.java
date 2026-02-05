package com.code.HushChat.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);
    private static final int MIN_KEY_LENGTH_BYTES = 32; // 256 bits

    @Value("${jwt.secret}")
    private String jwtSecretRaw;

    @Value("${jwt.expiration}")
    private long jwtExpirationMs;

    private SecretKey signingKey;

    @PostConstruct
    public void init() {
        try {
            if (jwtSecretRaw == null || jwtSecretRaw.isBlank()) {
                throw new IllegalStateException("jwt.secret is missing or empty");
            }

            // 🔒 Sanitize secret (THIS is what fixes your crash)
            String sanitizedSecret = jwtSecretRaw
                    .trim()
                    .replace("\"", "")
                    .replace("'", "");

            byte[] keyBytes = Base64.getMimeDecoder().decode(sanitizedSecret);

            if (keyBytes.length < MIN_KEY_LENGTH_BYTES) {
                throw new IllegalStateException(
                        "JWT secret key is too short. Required: 256 bits (32 bytes). " +
                        "Generate using: openssl rand -base64 32"
                );
            }

            this.signingKey = Keys.hmacShaKeyFor(keyBytes);

            logger.info("✅ JWT initialized successfully ({}-bit key)", keyBytes.length * 8);

        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "❌ Invalid Base64 JWT secret. " +
                    "Do NOT wrap it in quotes. " +
                    "Generate using: openssl rand -base64 32",
                    e
            );
        }
    }

    public String generateToken(String userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
                .subject(userId)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String getUserIdFromToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
        } catch (ExpiredJwtException e) {
            return e.getClaims().getSubject();
        } catch (JwtException e) {
            return null;
        }
    }

    public SecretKey getSigningKey() {
        return signingKey;
    }
}
