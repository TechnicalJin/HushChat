package com.code.HushChat.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT Token Provider for generating and validating JWT tokens.
 * This class handles all JWT operations including token generation,
 * validation, and claims extraction.
 * 
 * @author Hush Chat Security Team
 * @since 1.0
 */
@Component
public class JwtTokenProvider {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpirationMs;

    /**
     * Generates a JWT token for the specified user ID.
     * The token includes the user ID as the subject claim, along with
     * issued-at and expiration timestamps.
     *
     * @param userId the unique identifier of the user
     * @return JWT token as a string, or null if generation fails
     */
    public String generateToken(String userId) {
        try {
            Date now = new Date();
            Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

            return Jwts.builder()
                    .subject(userId)
                    .issuedAt(now)
                    .expiration(expiryDate)
                    .signWith(key, Jwts.SIG.HS256)
                    .compact();
        } catch (Exception e) {
            logger.error("Error generating JWT token for userId: {}", userId, e);
            return null;
        }
    }

    /**
     * Validates the provided JWT token.
     * Checks for proper signature, expiration, and format.
     *
     * @param token the JWT token to validate
     * @return true if the token is valid, false otherwise
     */
    public boolean validateToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            
            return true;
        } catch (SignatureException e) {
            logger.error("Invalid JWT signature: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            logger.error("Invalid JWT token format: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            logger.error("JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            logger.error("JWT token is unsupported: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.error("JWT claims string is empty: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error validating JWT token: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Extracts the user ID from the JWT token.
     * The user ID is stored in the subject (sub) claim of the token.
     *
     * @param token the JWT token
     * @return the user ID extracted from the token, or null if extraction fails
     */
    public String getUserIdFromToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            
            return claims.getSubject();
        } catch (SignatureException e) {
            logger.error("Invalid JWT signature while extracting userId: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            logger.error("Invalid JWT token format while extracting userId: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            logger.error("JWT token is expired while extracting userId: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            logger.error("JWT token is unsupported while extracting userId: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.error("JWT claims string is empty while extracting userId: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error extracting userId from JWT token: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Checks if the provided JWT token has expired.
     * 
     * @param token the JWT token to check
     * @return true if the token is expired, false if it's still valid or if check fails
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
            // Token is expired
            return true;
        } catch (SignatureException e) {
            logger.error("Invalid JWT signature while checking expiration: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            logger.error("Invalid JWT token format while checking expiration: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            logger.error("JWT token is unsupported while checking expiration: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.error("JWT claims string is empty while checking expiration: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error checking JWT token expiration: {}", e.getMessage());
        }
        return false;
    }
}
