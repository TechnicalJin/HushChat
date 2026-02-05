package com.code.HushChat.realtime;

import com.code.HushChat.model.event.RealtimeEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * WebSocket implementation of RealtimeDispatcher.
 * Active when realtime.mode=WEBSOCKET.
 * 
 * STUB - To be implemented in future phase.
 * 
 * Future implementation will:
 * - Inject SimpMessagingTemplate (Spring WebSocket messaging)
 * - Send events to /topic/room/{roomCode} for broadcast
 * - Send events to /queue/user/{userId} for targeted messages
 * - Respect excludeUserId by filtering recipients
 * - Handle disconnected users gracefully
 * 
 * CRITICAL SAFETY REQUIREMENTS (Phase 5):
 * - dispatch() must NEVER throw exceptions
 * - Failures must be logged, never propagated
 * - WebSocket errors must NOT break Long Polling path
 * - Poll endpoint must continue working even if WebSocket broken
 * 
 * @since 1.0.0
 */
@Component
@ConditionalOnProperty(
    prefix = "realtime",
    name = "mode",
    havingValue = "WEBSOCKET"
)
@Slf4j
public class WebSocketDispatcher implements RealtimeDispatcher {
    
    // TODO: Inject SimpMessagingTemplate
    // private final SimpMessagingTemplate messagingTemplate;
    // private final WebSocketSessionRegistry sessionRegistry;
    
    /**
     * Dispatch event via WebSocket.
     * 
     * SAFETY: Must never throw exceptions - all errors caught and logged.
     * Clients will fall back to polling if WebSocket delivery fails.
     * 
     * @param event The real-time event to dispatch
     * @param excludeUserId Optional user ID to exclude from notification
     */
    @Override
    public void dispatch(RealtimeEvent event, @Nullable String excludeUserId) {
        try {
            // TODO: Implementation steps:
            // 1. Check if event should be broadcast or targeted
            // 2. Get active WebSocket sessions for room from sessionRegistry
            // 3. Filter out excludeUserId if present
            // 4. Send event to /topic/room/{roomCode} OR /queue/user/{userId}
            // 5. Log delivery success/failure
            // 6. CRITICAL: Catch ALL exceptions - never propagate
            
            log.warn("WebSocket dispatcher called but not yet implemented - event dropped: {}", 
                     event.getEventType());
                     
        } catch (Exception e) {
            // CRITICAL: Isolate WebSocket failures from polling path
            log.error("WebSocket dispatch failed for event {} in room {} (clients will receive via polling): {}", 
                     event.getEventType(), event.getRoomCode(), e.getMessage());
            // Do NOT rethrow - polling continues to work
        }
    }
}
