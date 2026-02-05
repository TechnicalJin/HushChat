package com.code.HushChat.realtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

/**
 * Custom handshake handler that creates StompPrincipal for WebSocket sessions.
 * 
 * Responsibilities:
 * - Extract userId from session attributes (set by WebSocketAuthInterceptor)
 * - Create StompPrincipal with userId as the name
 * - Assign Principal to WebSocket session for STOMP user destination routing
 * 
 * Why needed:
 * - Spring STOMP's convertAndSendToUser() requires sessions to have a Principal
 * - The Principal name must match the userId parameter in convertAndSendToUser()
 * - Without this, user-specific messages cannot be routed to their destination
 * 
 * Execution flow:
 * 1. WebSocketAuthInterceptor validates JWT and stores userId in attributes
 * 2. This handler reads userId from attributes
 * 3. Creates StompPrincipal(userId) and assigns to session
 * 4. Spring STOMP can now route messages via convertAndSendToUser()
 * 
 * @since 1.0.0
 */
@Component
@Slf4j
public class CustomHandshakeHandler extends DefaultHandshakeHandler {
    
    /**
     * Session attribute key for userId (set by WebSocketAuthInterceptor).
     */
    private static final String USER_ID_ATTRIBUTE = "userId";
    
    /**
     * Determine the user Principal for a WebSocket session.
     * 
     * Called during WebSocket handshake AFTER authentication interceptor runs.
     * 
     * @param request HTTP upgrade request
     * @param wsHandler WebSocket handler
     * @param attributes Session attributes populated by interceptors
     * @return Principal identifying the user, or null if userId not found
     */
    @Override
    protected Principal determineUser(
            ServerHttpRequest request,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        
        // Extract userId from session attributes (set by WebSocketAuthInterceptor)
        Object userIdObj = attributes.get(USER_ID_ATTRIBUTE);
        
        if (userIdObj == null) {
            log.warn("Cannot create StompPrincipal: userId not found in session attributes. " +
                     "Ensure WebSocketAuthInterceptor runs before this handler.");
            return null;
        }
        
        String userId = userIdObj.toString();
        
        if (userId.isEmpty()) {
            log.warn("Cannot create StompPrincipal: userId is empty");
            return null;
        }
        
        // Create StompPrincipal (name = userId)
        StompPrincipal principal = new StompPrincipal(userId);
        
        log.debug("Created StompPrincipal for WebSocket session: userId={}", userId);
        
        return principal;
    }
}
