package com.code.HushChat.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Component
public class TokenUtil {
    
    @Value("${hush.jwt.secret:hush-chat-super-secret-key-change-in-production}")
    private String jwtSecret;
    
    @Value("${hush.jwt.issuer:hush-chat}")
    private String jwtIssuer;
    
    private static final String USER_ID_CLAIM = "userId";
    private static final String DEVICE_ID_CLAIM = "deviceId";
    
    /**
     * Generate JWT token for user session
     */
    public String generateToken(String userId, String deviceId, LocalDateTime expiryTime) {
        Algorithm algorithm = Algorithm.HMAC256(jwtSecret);
        
        Date expiryDate = Date.from(expiryTime.atZone(ZoneId.systemDefault()).toInstant());
        
        return JWT.create()
                .withIssuer(jwtIssuer)
                .withSubject(userId)
                .withClaim(USER_ID_CLAIM, userId)
                .withClaim(DEVICE_ID_CLAIM, deviceId)
                .withIssuedAt(new Date())
                .withExpiresAt(expiryDate)
                .sign(algorithm);
    }
    
    /**
     * Validate and decode JWT token
     */
    public DecodedJWT validateToken(String token) throws JWTVerificationException {
        Algorithm algorithm = Algorithm.HMAC256(jwtSecret);
        JWTVerifier verifier = JWT.require(algorithm)
                .withIssuer(jwtIssuer)
                .build();
        
        return verifier.verify(token);
    }
    
    /**
     * Extract user ID from token
     */
    public String extractUserId(String token) {
        try {
            DecodedJWT jwt = validateToken(token);
            return jwt.getClaim(USER_ID_CLAIM).asString();
        } catch (JWTVerificationException e) {
            return null;
        }
    }
    
    /**
     * Extract device ID from token
     */
    public String extractDeviceId(String token) {
        try {
            DecodedJWT jwt = validateToken(token);
            return jwt.getClaim(DEVICE_ID_CLAIM).asString();
        } catch (JWTVerificationException e) {
            return null;
        }
    }
    
    /**
     * Check if token is expired
     */
    public boolean isTokenExpired(String token) {
        try {
            DecodedJWT jwt = validateToken(token);
            return jwt.getExpiresAt().before(new Date());
        } catch (JWTVerificationException e) {
            return true;
        }
    }
}