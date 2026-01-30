package com.code.HushChat.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * JWT Token Utility for generating and validating session tokens.
 * This is a wrapper around JwtTokenProvider to support legacy session management
 * with additional claims like deviceId.
 */
@Component
@Slf4j
public class TokenUtil {
    
    @Value("${jwt.secret}")
    private String jwtSecret;
    
    @Value("${hush.jwt.issuer:hush-chat}")
    private String jwtIssuer;
    
    private static final String USER_ID_CLAIM = "userId";
    private static final String DEVICE_ID_CLAIM = "deviceId";
    
    /**
     * Generate JWT token for user session with deviceId
     */
    public String generateToken(String userId, String deviceId, LocalDateTime expiryTime) {
        try {
            Date expiryDate = Date.from(expiryTime.atZone(ZoneId.systemDefault()).toInstant());
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            
            return Jwts.builder()
                    .subject(userId)
                    .claim(USER_ID_CLAIM, userId)
                    .claim(DEVICE_ID_CLAIM, deviceId)
                    .issuer(jwtIssuer)
                    .issuedAt(new Date())
                    .expiration(expiryDate)
                    .signWith(key, Jwts.SIG.HS256)
                    .compact();
        } catch (Exception e) {
            log.error("Error generating JWT token for userId: {}, deviceId: {}", userId, deviceId, e);
            return null;
        }
    }
    
    /**
     * Validate JWT token
     * @throws JwtException if token is invalid
     */
    public void validateToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            
            Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(jwtIssuer)
                    .build()
                    .parseSignedClaims(token);
        } catch (SignatureException e) {
            log.error("Invalid JWT signature: {}", e.getMessage());
            throw e;
        } catch (MalformedJwtException e) {
            log.error("Invalid JWT token format: {}", e.getMessage());
            throw e;
        } catch (ExpiredJwtException e) {
            log.error("JWT token is expired: {}", e.getMessage());
            throw e;
        } catch (UnsupportedJwtException e) {
            log.error("JWT token is unsupported: {}", e.getMessage());
            throw e;
        } catch (IllegalArgumentException e) {
            log.error("JWT claims string is empty: {}", e.getMessage());
            throw e;
        }
    }
    
    /**
     * Extract user ID from token
     */
    public String extractUserId(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            
            return claims.get(USER_ID_CLAIM, String.class);
        } catch (Exception e) {
            log.error("Error extracting userId from token: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Extract device ID from token
     */
    public String extractDeviceId(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            
            return claims.get(DEVICE_ID_CLAIM, String.class);
        } catch (Exception e) {
            log.error("Error extracting deviceId from token: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Check if token is expired
     */
    public boolean isTokenExpired(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            
            Date expiration = claims.getExpiration();
            return expiration.before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        } catch (Exception e) {
            log.error("Error checking token expiration: {}", e.getMessage());
            return true;
        }
    }
}