package com.code.HushChat.realtime;

import java.security.Principal;

/**
 * Custom Principal implementation for STOMP WebSocket sessions.
 * 
 * Purpose:
 * - Uniquely identifies WebSocket users by userId
 * - Enables Spring STOMP's convertAndSendToUser() to route messages correctly
 * - Required for user-specific destination resolution (/user/queue/...)
 * 
 * Why needed:
 * - convertAndSendToUser(userId, destination, payload) matches userId against Principal.getName()
 * - Without a Principal, Spring cannot resolve which sessions belong to which user
 * - This class wraps userId as a Principal for STOMP session identification
 * 
 * Usage:
 * - Created during STOMP CONNECT authentication by StompConnectAuthInterceptor
 * - Stored in WebSocket session and accessible via StompHeaderAccessor
 * - Used by Spring's UserDestinationMessageHandler for message routing
 * 
 * @since 1.0.0
 */
public class StompPrincipal implements Principal {
    
    private final String userId;
    
    /**
     * Create a new StompPrincipal.
     * 
     * @param userId Unique user identifier (must match userId in session attributes)
     */
    public StompPrincipal(String userId) {
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("userId cannot be null or empty");
        }
        this.userId = userId;
    }
    
    /**
     * Get the Principal's name (userId).
     * This is called by Spring STOMP when routing user-specific messages.
     * 
     * @return User ID that uniquely identifies this WebSocket session
     */
    @Override
    public String getName() {
        return userId;
    }
    
    /**
     * Get the user ID.
     * 
     * @return User ID
     */
    public String getUserId() {
        return userId;
    }
    
    @Override
    public String toString() {
        return "StompPrincipal{userId='" + userId + "'}";
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StompPrincipal that = (StompPrincipal) o;
        return userId.equals(that.userId);
    }
    
    @Override
    public int hashCode() {
        return userId.hashCode();
    }
}
