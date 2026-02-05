package com.code.HushChat.realtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WebSocket event handler for connection lifecycle management.
 * 
 * Handles:
 * - CONNECT: User establishes WebSocket connection
 * - SUBSCRIBE: User subscribes to a room topic
 * - UNSUBSCRIBE: User unsubscribes from a room topic
 * - DISCONNECT: User closes WebSocket connection
 * 
 * Responsibilities:
 * - Register sessions in WebSocketSessionRegistry
 * - Track room subscriptions
 * - Clean up on disconnect
 * - Log connection events
 * 
 * Session lifecycle:
 * 1. CONNECT -> Extract userId from session attributes
 * 2. SUBSCRIBE -> Extract roomCode from destination, register session
 * 3. UNSUBSCRIBE -> Optional cleanup (session persists)
 * 4. DISCONNECT -> Unregister session, clean up all mappings
 * 
 * @since 1.0.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventHandler {
    
    private final WebSocketSessionRegistry sessionRegistry;
    
    /**
     * Pattern to extract room code from subscription destination.
     * Expected format: /topic/room/{roomCode}
     */
    /**
     * Pattern to extract room code from subscription destination.
     * Expected format: /topic/room/{roomCode}
     */
    private static final Pattern ROOM_TOPIC_PATTERN = Pattern.compile("/topic/room/([A-Z0-9]+)");

    /**
     * Pattern to extract room code from user queue subscription.
     * Expected format: /user/queue/room/{roomCode} or /user/../queue/room/{roomCode}
     * Matches: /user/queue/room/ABC, /queue/room/ABC, /user/{id}/queue/room/ABC
     */
    private static final Pattern ROOM_QUEUE_PATTERN = Pattern.compile(".*queue/room/([A-Z0-9]+)");
    
    /**
     * Session attribute key for userId (set by WebSocketAuthInterceptor).
     */
    private static final String USER_ID_ATTRIBUTE = "userId";
    
    /**
     * Handle WebSocket connection established.
     * 
     * Note: We don't register the session yet because we don't know
     * which room the user will subscribe to. Registration happens
     * on SUBSCRIBE event.
     * 
     * @param event Session connected event
     */
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        String userId = getUserIdFromSession(headerAccessor);
        
        log.info("WebSocket connection established: sessionId={}, userId={}", 
                 sessionId, userId != null ? userId : "unknown");
    }
    
    /**
     * Handle user subscribing to a room topic.
     * 
     * This is where we register the session in the registry,
     * because now we know both userId and roomCode.
     * 
     * Expected destination format: 
     * - /topic/room/{roomCode} (Broadcast)
     * - /user/queue/room/{roomCode} (Targeted)
     * 
     * @param event Session subscribe event
     */
    @EventListener
    public void handleWebSocketSubscribeListener(SessionSubscribeEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        String destination = headerAccessor.getDestination();
        String userId = getUserIdFromSession(headerAccessor);
        
        if (userId == null) {
            log.warn("Cannot register subscription: userId not found in session attributes");
            return;
        }
        
        if (destination == null) {
            log.warn("Cannot register subscription: destination is null");
            return;
        }
        
        // Extract room code from destination
        String roomCode = extractRoomCodeFromDestination(destination);
        
        if (roomCode != null) {
            // Register session in registry
            sessionRegistry.registerSession(sessionId, userId, roomCode);
            
            log.info("User registered in room via WebSocket: sessionId={}, userId={}, roomCode={}, destination={}", 
                     sessionId, userId, roomCode, destination);
        } else {
            // Non-room subscription (e.g., user queue)
            log.debug("User subscribed to non-room destination: sessionId={}, userId={}, destination={}", 
                      sessionId, userId, destination);
        }
    }
    
    /**
     * Handle user unsubscribing from a topic.
     * 
     * Note: We keep the session registered even after unsubscribe,
     * because the user might subscribe to another room.
     * Full cleanup happens on DISCONNECT.
     * 
     * @param event Session unsubscribe event
     */
    @EventListener
    public void handleWebSocketUnsubscribeListener(SessionUnsubscribeEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        String userId = getUserIdFromSession(headerAccessor);
        
        log.debug("User unsubscribed: sessionId={}, userId={}", 
                  sessionId, userId != null ? userId : "unknown");
        
        // Optional: Could implement partial cleanup if needed
        // For now, we keep the session registered until disconnect
    }
    
    /**
     * Handle WebSocket disconnection.
     * 
     * Performs full cleanup:
     * - Unregister session from registry
     * - Remove user from all room mappings
     * - Clean up empty collections
     * 
     * @param event Session disconnect event
     */
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        String userId = getUserIdFromSession(headerAccessor);
        
        log.info("WebSocket disconnected: sessionId={}, userId={}", 
                 sessionId, userId != null ? userId : "unknown");
        
        // Unregister session from registry (handles all cleanup)
        sessionRegistry.unregisterSession(sessionId);
        
        // Log active connection stats
        log.debug("Active WebSocket stats: sessions={}, users={}, rooms={}", 
                  sessionRegistry.getTotalSessionCount(),
                  sessionRegistry.getActiveUserCount(),
                  sessionRegistry.getActiveRoomCount());
    }
    
    /**
     * Extract userId from session attributes.
     * 
     * userId is set by WebSocketAuthInterceptor during handshake.
     * 
     * @param headerAccessor STOMP header accessor
     * @return userId, or null if not found
     */
    private String getUserIdFromSession(StompHeaderAccessor headerAccessor) {
        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
        
        if (sessionAttributes == null) {
            return null;
        }
        
        Object userId = sessionAttributes.get(USER_ID_ATTRIBUTE);
        return userId != null ? userId.toString() : null;
    }
    
    /**
     * Extract room code from subscription destination.
     * 
     * Supports:
     * - /topic/room/{roomCode}
     * - /user/queue/room/{roomCode}
     * - /queue/room/{roomCode}
     * 
     * @param destination Subscription destination
     * @return Room code, or null if not a room subscription
     */
    private String extractRoomCodeFromDestination(String destination) {
        if (destination == null) {
            return null;
        }
        
        // Check topic pattern
        Matcher topicMatcher = ROOM_TOPIC_PATTERN.matcher(destination);
        if (topicMatcher.matches()) {
            return topicMatcher.group(1);
        }
        
        // Check queue pattern
        Matcher queueMatcher = ROOM_QUEUE_PATTERN.matcher(destination);
        if (queueMatcher.matches()) {
            return queueMatcher.group(1);
        }
        
        return null;
    }
}