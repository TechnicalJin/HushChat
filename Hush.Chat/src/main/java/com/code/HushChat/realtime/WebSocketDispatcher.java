package com.code.HushChat.realtime;

import com.code.HushChat.model.event.RealtimeEvent;
import com.code.HushChat.util.EventConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.Nullable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * WebSocket implementation of RealtimeDispatcher.
 * Active when realtime.mode=WEBSOCKET.
 * 
 * Delivers real-time events to connected WebSocket clients using STOMP protocol.
 * 
 * Delivery strategies:
 * 1. Room broadcast: Send to all users in room (except excludeUserId)
 * 2. Targeted delivery: Send to specific users via /user destination
 * 3. Fallback: Silently fail if delivery fails (clients use polling)
 * 
 * Message destinations:
 * - /topic/room/{roomCode}: Broadcast to all room subscribers
 * - /user/{userId}/queue/events: Targeted user messages (future)
 * 
 * Safety guarantees (Phase 5):
 * - dispatch() NEVER throws exceptions
 * - All errors caught and logged
 * - WebSocket failures DON'T break Long Polling path
 * - Clients automatically fall back to polling on failure
 * 
 * @since 1.0.0
 */
@Component
@ConditionalOnProperty(
    prefix = "realtime",
    name = "mode",
    havingValue = "WEBSOCKET"
)
@RequiredArgsConstructor
@Slf4j
public class WebSocketDispatcher implements RealtimeDispatcher {
    
    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketSessionRegistry sessionRegistry;
    private final EventConverter eventConverter;
    
    /**
     * Destination prefix for room topics.
     */
    private static final String ROOM_TOPIC_PREFIX = "/topic/room/";
    
    /**
     * Dispatch event via WebSocket to all connected clients in the room.
     * 
     * Flow:
     * 1. Get active WebSocket users in room from session registry
     * 2. Filter out excludeUserId if provided
     * 3. Convert RealtimeEvent to PollEventDto (unified format)
     * 4. Send to /topic/room/{roomCode} for broadcast
     * 5. Log success/failure
     * 
     * CRITICAL SAFETY:
     * - Never throws exceptions (all errors caught and logged)
     * - WebSocket failures don't affect Long Polling
     * - Clients receive events via polling if WebSocket fails
     * 
     * @param event The real-time event to dispatch
     * @param excludeUserId Optional user ID to exclude from notification (typically the sender)
     */
    @Override
    public void dispatch(RealtimeEvent event, @Nullable String excludeUserId) {
        try {
            // Validate event
            if (event == null) {
                log.warn("Cannot dispatch null event via WebSocket");
                return;
            }
            
            String roomCode = event.getRoomCode();
            if (roomCode == null || roomCode.isEmpty()) {
                log.warn("Cannot dispatch event without roomCode: eventType={}", event.getEventType());
                return;
            }
            
            // Get active WebSocket users in room
            Set<String> connectedUsers = sessionRegistry.getUserIdsInRoom(roomCode);
            
            if (connectedUsers.isEmpty()) {
                // No WebSocket users in room - this is normal (they'll use polling)
                log.debug("No WebSocket users in room {} - event will be delivered via polling", roomCode);
                return;
            }
            
            // Filter out excluded user (typically the sender)
            Set<String> recipients = new java.util.HashSet<>(connectedUsers);
            if (excludeUserId != null && recipients.contains(excludeUserId)) {
                recipients.remove(excludeUserId);
                log.debug("Excluding user {} from WebSocket broadcast in room {}", excludeUserId, roomCode);
            }
            
            if (recipients.isEmpty()) {
                log.debug("All WebSocket users excluded from event in room {}", roomCode);
                return;
            }
            
            // Convert to DTO format (same as Long Polling)
            Object payload = eventConverter.toDto(event);
            
            // Build destination
            String destination = ROOM_TOPIC_PREFIX + roomCode;
            
            // Send to each recipient individually to enforce excludeUserId filtering
            // This prevents the sender from receiving their own events via WebSocket
            int sentCount = 0;
            for (String userId : recipients) {
                try {
                    messagingTemplate.convertAndSendToUser(userId, "/queue/room/" + roomCode, payload);
                    sentCount++;
                } catch (Exception e) {
                    log.warn("Failed to send WebSocket event to user {}: {}", userId, e.getMessage());
                    // Continue sending to other users
                }
            }
            
            if (sentCount == 0) {
                log.warn("WebSocket event not delivered to any user in room {} (all sends failed)", roomCode);
            }
            
            log.info("WebSocket event dispatched: eventType={}, roomCode={}, recipients={}/{}, destination={}", 
                     event.getEventType(), roomCode, sentCount, recipients.size(), destination);
            
        } catch (Exception e) {
            // CRITICAL SAFETY: Isolate WebSocket failures from Long Polling
            // Clients will automatically fall back to polling if WebSocket fails
            log.error("WebSocket dispatch failed for event {} in room {} (clients will receive via polling): {}", 
                     event.getEventType(), 
                     event.getRoomCode(), 
                     e.getMessage());
            
            // Log full stack trace at debug level for troubleshooting
            log.debug("WebSocket dispatch exception details:", e);
            
            // DO NOT RETHROW - Long Polling must continue to work
        }
    }
    
    /**
     * Send targeted event to a specific user (future enhancement).
     * 
     * This method can be used for:
     * - Private notifications
     * - User-specific updates
     * - Typing indicators
     * 
     * Destination: /user/{userId}/queue/events
     * 
     * @param event The event to send
     * @param userId Target user ID
     */
    public void sendToUser(RealtimeEvent event, String userId) {
        try {
            // Check if user is connected via WebSocket
            if (!sessionRegistry.isUserConnected(userId)) {
                log.debug("User {} not connected via WebSocket - skipping targeted delivery", userId);
                return;
            }
            
            // Convert to DTO
            Object payload = eventConverter.toDto(event);
            
            // Send to user-specific queue
            messagingTemplate.convertAndSendToUser(userId, "/queue/events", payload);
            
            log.debug("Targeted WebSocket event sent to user {}: eventType={}", 
                      userId, event.getEventType());
            
        } catch (Exception e) {
            log.error("Failed to send targeted WebSocket event to user {}: {}", 
                      userId, e.getMessage());
            log.debug("Targeted delivery exception details:", e);
        }
    }
    
    /**
     * Get count of active WebSocket users in a room.
     * Useful for monitoring and debugging.
     * 
     * @param roomCode Room code to query
     * @return Number of active WebSocket users
     */
    public int getActiveUserCount(String roomCode) {
        try {
            return sessionRegistry.getUserIdsInRoom(roomCode).size();
        } catch (Exception e) {
            log.error("Failed to get active user count for room {}: {}", roomCode, e.getMessage());
            return 0;
        }
    }
    
    /**
     * Check if a specific user is connected via WebSocket.
     * 
     * @param userId User ID to check
     * @return true if user has at least one active WebSocket session
     */
    public boolean isUserConnected(String userId) {
        try {
            return sessionRegistry.isUserConnected(userId);
        } catch (Exception e) {
            log.error("Failed to check user connection status for {}: {}", userId, e.getMessage());
            return false;
        }
    }
}