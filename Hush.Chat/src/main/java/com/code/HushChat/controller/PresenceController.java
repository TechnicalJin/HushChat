package com.code.HushChat.controller;

import com.code.HushChat.dto.WebSocketEventDto;
import com.code.HushChat.model.event.BaseRealtimeEvent;
import com.code.HushChat.model.event.EventType;
import com.code.HushChat.model.event.RealtimeEvent;
import com.code.HushChat.realtime.RealtimeDispatcher;
import com.code.HushChat.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * WebSocket controller for user presence events.
 * 
 * Handles:
 * - PRESENCE_ACTIVE: User tab is visible and connected
 * - PRESENCE_INACTIVE: User tab is hidden or disconnected
 * 
 * PRESENCE RULE:
 * User is ACTIVE if and only if:
 *   - WebSocket is connected
 *   - AND browser tab is visible (document.visibilityState === "visible")
 * 
 * @since 1.0.0
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class PresenceController {
    
    private final PresenceService presenceService;
    private final RealtimeDispatcher realtimeDispatcher;
    
    /**
     * Handle PRESENCE_ACTIVE message from client.
     * Client sends this when:
     * - WebSocket connects AND tab is visible
     * - Tab becomes visible (visibilitychange event)
     * 
     * @param payload Map containing roomCode
     * @param headerAccessor STOMP header accessor
     * @param principal User principal (contains userId)
     */
    @MessageMapping("/presence/active")
    public void handlePresenceActive(
            @Payload Map<String, String> payload,
            SimpMessageHeaderAccessor headerAccessor,
            Principal principal
    ) {
        try {
            String userId = extractUserId(principal);
            String roomCode = payload.get("roomCode");
            
            if (userId == null || roomCode == null) {
                log.warn("Invalid presence active request: userId={}, roomCode={}", userId, roomCode);
                return;
            }
            
            // Update presence: connected=true, visible=true
            presenceService.updateConnection(roomCode, userId, true);
            presenceService.updateVisibility(roomCode, userId, true);
            
            log.info("User presence ACTIVE: userId={}, roomCode={}", userId, roomCode);
            
            // Broadcast presence update to room
            broadcastPresenceUpdate(roomCode, userId);
            
        } catch (Exception e) {
            log.error("Error handling presence active", e);
        }
    }
    
    /**
     * Handle PRESENCE_INACTIVE message from client.
     * Client sends this when:
     * - Tab becomes hidden (visibilitychange event)
     * - Browser minimized
     * - User switches to another tab
     * 
     * @param payload Map containing roomCode
     * @param headerAccessor STOMP header accessor
     * @param principal User principal (contains userId)
     */
    @MessageMapping("/presence/inactive")
    public void handlePresenceInactive(
            @Payload Map<String, String> payload,
            SimpMessageHeaderAccessor headerAccessor,
            Principal principal
    ) {
        try {
            String userId = extractUserId(principal);
            String roomCode = payload.get("roomCode");
            
            if (userId == null || roomCode == null) {
                log.warn("Invalid presence inactive request: userId={}, roomCode={}", userId, roomCode);
                return;
            }
            
            // Update presence: visible=false (keep connected=true)
            presenceService.updateVisibility(roomCode, userId, false);
            
            log.info("User presence INACTIVE: userId={}, roomCode={}", userId, roomCode);
            
            // Broadcast presence update to room
            broadcastPresenceUpdate(roomCode, userId);
            
        } catch (Exception e) {
            log.error("Error handling presence inactive", e);
        }
    }
    
    /**
     * Broadcast presence update to all users in room.
     * 
     * @param roomCode Room code
     * @param userId User whose presence changed
     */
    private void broadcastPresenceUpdate(String roomCode, String userId) {
        try {
            // Get all presence statuses for the room
            Map<String, Boolean> presenceMap = presenceService.getRoomPresence(roomCode);
            int activeCount = presenceService.getActiveUserCount(roomCode);
            
            // Create presence DTO as payload
            WebSocketEventDto presenceDto = WebSocketEventDto.presenceEvent(
                roomCode, 
                presenceMap, 
                activeCount
            );
            
            // Create realtime event
            RealtimeEvent event = BaseRealtimeEvent.builder()
                .eventType(EventType.USER_PRESENCE)
                .roomCode(roomCode)
                .timestamp(LocalDateTime.now())
                .payload(presenceDto)
                .broadcast(true)
                .triggeredBy(userId)
                .build();
            
            // Broadcast to room (no exclusion - everyone needs presence updates)
            realtimeDispatcher.dispatch(event, null);
            
            log.debug("Presence update broadcasted: roomCode={}, activeCount={}, totalUsers={}", 
                      roomCode, activeCount, presenceMap.size());
            
        } catch (Exception e) {
            log.error("Error broadcasting presence update: roomCode={}, userId={}", roomCode, userId, e);
        }
    }
    
    /**
     * Extract userId from Principal.
     * 
     * @param principal User principal
     * @return userId or null
     */
    private String extractUserId(Principal principal) {
        if (principal == null) {
            return null;
        }
        return principal.getName();
    }
}
