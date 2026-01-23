package com.code.HushChat.service;

import com.code.HushChat.config.AppConfig;
import com.code.HushChat.exception.UnauthorizedException;
import com.code.HushChat.model.UserSession;
import com.code.HushChat.storage.InMemorySessionStore;
import com.code.HushChat.util.CodeGenerator;
import com.code.HushChat.util.TokenUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {
    
    private final InMemorySessionStore sessionStore;
    private final TokenUtil tokenUtil;
    private final AppConfig appConfig;
    
    /**
     * Create new user session after OTP verification
     */
    public UserSession createSession(String deviceId) {
        // Generate unique user ID
        String userId = CodeGenerator.generateUserId();
        
        // Calculate expiry time
        LocalDateTime expiryTime = LocalDateTime.now()
            .plusHours(appConfig.getSession().getExpiryHours());
        
        // Generate JWT token
        String sessionToken = tokenUtil.generateToken(userId, deviceId, expiryTime);
        
        // Create session
        UserSession session = UserSession.builder()
            .sessionToken(sessionToken)
            .deviceId(deviceId)
            .userId(userId)
            .createdAt(LocalDateTime.now())
            .expiryTime(expiryTime)
            .lastActivity(LocalDateTime.now())
            .build();
        
        // Store session
        sessionStore.save(sessionToken, session);
        
        log.info("Session created for user: {} (device: {})", 
            userId, deviceId.substring(0, Math.min(8, deviceId.length())) + "...");
        
        return session;
    }
    
    /**
     * Validate session token and return session
     */
    public UserSession validateSession(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) {
            throw new UnauthorizedException("Session token is required");
        }
        
        // Get session from store
        UserSession session = sessionStore.get(sessionToken);
        
        if (session == null) {
            throw new UnauthorizedException("Invalid session. Please login again.");
        }
        
        // Check if session expired
        if (session.isExpired()) {
            sessionStore.delete(sessionToken);
            throw new UnauthorizedException("Session has expired. Please login again.");
        }
        
        // Validate token integrity
        try {
            tokenUtil.validateToken(sessionToken);
        } catch (Exception e) {
            sessionStore.delete(sessionToken);
            throw new UnauthorizedException("Invalid session token. Please login again.");
        }
        
        // Update last activity
        session.updateActivity();
        sessionStore.save(sessionToken, session);
        
        return session;
    }
    
    /**
     * Get user ID from session token
     */
    public String getUserId(String sessionToken) {
        UserSession session = validateSession(sessionToken);
        return session.getUserId();
    }
    
    /**
     * Logout and invalidate session
     */
    public void logout(String sessionToken) {
        UserSession session = sessionStore.get(sessionToken);
        
        if (session != null) {
            sessionStore.delete(sessionToken);
            log.info("User logged out: {}", session.getUserId());
        }
    }
    
    /**
     * Check if session exists and is valid
     */
    public boolean isSessionValid(String sessionToken) {
        try {
            validateSession(sessionToken);
            return true;
        } catch (UnauthorizedException e) {
            return false;
        }
    }
    
    /**
     * Extend session expiry (refresh session)
     */
    public UserSession refreshSession(String sessionToken) {
        UserSession session = validateSession(sessionToken);
        
        // Extend expiry time
        LocalDateTime newExpiryTime = LocalDateTime.now()
            .plusHours(appConfig.getSession().getExpiryHours());
        
        session.setExpiryTime(newExpiryTime);
        session.updateActivity();
        
        sessionStore.save(sessionToken, session);
        
        log.info("Session refreshed for user: {}", session.getUserId());
        
        return session;
    }
    
    /**
     * Get all active sessions count
     */
    public int getActiveSessionCount() {
        return sessionStore.size();
    }
    
    /**
     * Force logout user by userId
     */
    public void logoutUser(String userId) {
        sessionStore.getAll().entrySet().stream()
            .filter(entry -> entry.getValue().getUserId().equals(userId))
            .forEach(entry -> sessionStore.delete(entry.getKey()));
        
        log.info("All sessions terminated for user: {}", userId);
    }
}