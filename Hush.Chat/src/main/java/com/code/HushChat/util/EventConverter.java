package com.code.HushChat.util;

import com.code.HushChat.dto.WebSocketEventDto;
import com.code.HushChat.model.event.RealtimeEvent;
import lombok.extern.slf4j.Slf4j;

// NOTE: Long polling (PollEventDto) compatibility intentionally removed.
// All real-time communication now uses WebSocket-only transport.

/**
 * Utility class for converting RealtimeEvent to WebSocketEventDto.
 * 
 * This converter provides a clean interface for the WebSocket transport layer
 * to serialize events for delivery to clients.
 * 
 * @since 2.0.0 (WebSocket-only version)
 */
@Slf4j
@org.springframework.stereotype.Component
public class EventConverter {
    
    /**
     * Convert RealtimeEvent to DTO format for WebSocket transport.
     * 
     * Used by:
     * - WebSocketDispatcher (for serializing events to clients)
     * 
     * @param realtimeEvent Unified RealtimeEvent
     * @return DTO object suitable for serialization (WebSocketEventDto or payload)
     */
    public Object toDto(RealtimeEvent realtimeEvent) {
        if (realtimeEvent == null) {
            return null;
        }
        
        // If payload is already a WebSocketEventDto, return it directly
        if (realtimeEvent.getPayload() instanceof WebSocketEventDto) {
            return (WebSocketEventDto) realtimeEvent.getPayload();
        }
        
        // If payload exists but is not WebSocketEventDto, return it as-is
        // This supports custom event types
        if (realtimeEvent.getPayload() != null) {
            return realtimeEvent.getPayload();
        }
        
        // Fallback: create a basic WebSocketEventDto from the event
        WebSocketEventDto.EventType wsEventType = mapEventType(realtimeEvent.getEventType());
        
        return WebSocketEventDto.builder()
                .type(wsEventType)
                .roomCode(realtimeEvent.getRoomCode())
                .timestamp(realtimeEvent.getTimestamp())
                .build();
    }
    
    /**
     * Map unified EventType to WebSocketEventDto.EventType
     */
    private static WebSocketEventDto.EventType mapEventType(com.code.HushChat.model.event.EventType eventType) {
        if (eventType == null) {
            return WebSocketEventDto.EventType.MESSAGE;
        }
        
        return switch (eventType) {
            case MESSAGE, MESSAGE_FILE -> WebSocketEventDto.EventType.MESSAGE;
            case MESSAGE_EDIT -> WebSocketEventDto.EventType.MESSAGE_EDIT;
            case MESSAGE_DELETE -> WebSocketEventDto.EventType.MESSAGE_DELETE;
            case REACTION, REACTION_ADD, REACTION_REMOVE -> WebSocketEventDto.EventType.REACTION;
            case USER_PRESENCE -> WebSocketEventDto.EventType.PRESENCE;
            // Future event types default to MESSAGE
            default -> WebSocketEventDto.EventType.MESSAGE;
        };
    }
}
