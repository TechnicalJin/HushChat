package com.code.HushChat.util;

import com.code.HushChat.dto.PollEventDto;
import com.code.HushChat.model.event.BaseRealtimeEvent;
import com.code.HushChat.model.event.EventType;
import com.code.HushChat.model.event.RealtimeEvent;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for converting between PollEventDto (current) and RealtimeEvent (unified).
 * Ensures backward compatibility during migration from Long Polling to WebSocket.
 * 
 * This converter allows:
 * 1. Legacy code to continue using PollEventDto
 * 2. New code to use RealtimeEvent
 * 3. Gradual migration without breaking existing functionality
 * 
 * Can be used as:
 * - Static utility: EventConverter.toPollEventDto(event)
 * - Spring bean: Injected for instance methods (toDto)
 * 
 * @since 1.0.0
 */
@Slf4j
@org.springframework.stereotype.Component
public class EventConverter {
    
    /**
     * Convert PollEventDto to RealtimeEvent
     * 
     * @param pollEvent Legacy PollEventDto
     * @return Unified RealtimeEvent
     */
    public static RealtimeEvent toRealtimeEvent(PollEventDto pollEvent) {
        if (pollEvent == null) {
            return null;
        }
        
        EventType eventType = mapPollEventTypeToEventType(pollEvent.getType());
        
        return BaseRealtimeEvent.builder()
                .eventType(eventType)
                .roomCode(pollEvent.getRoomCode())
                .timestamp(pollEvent.getTimestamp())
                .payload(pollEvent) // Store entire PollEventDto as payload for now
                .broadcast(true)
                .build();
    }
    
    /**
     * Convert RealtimeEvent back to PollEventDto for Long Polling responses
     * 
     * @param realtimeEvent Unified RealtimeEvent
     * @return Legacy PollEventDto for HTTP response
     */
    public static PollEventDto toPollEventDto(RealtimeEvent realtimeEvent) {
        if (realtimeEvent == null) {
            return null;
        }
        
        // If payload is already a PollEventDto, return it directly
        if (realtimeEvent.getPayload() instanceof PollEventDto) {
            return (PollEventDto) realtimeEvent.getPayload();
        }
        
        // Otherwise, create a new PollEventDto (for future WebSocket events)
        PollEventDto.EventType pollEventType = mapEventTypeToPollEventType(realtimeEvent.getEventType());
        
        return PollEventDto.builder()
                .type(pollEventType)
                .roomCode(realtimeEvent.getRoomCode())
                .timestamp(realtimeEvent.getTimestamp())
                // Payload mapping would be done here based on event type
                // For now, we preserve backward compatibility
                .build();
    }
    
    /**
     * Convert RealtimeEvent to DTO format for transport layer.
     * This is an instance method alias for toPollEventDto to support both
     * static and instance usage patterns.
     * 
     * Used by:
     * - WebSocketDispatcher (instance method)
     * - LongPollDispatcher (static method)
     * 
     * @param realtimeEvent Unified RealtimeEvent
     * @return DTO object suitable for serialization (PollEventDto)
     */
    public Object toDto(RealtimeEvent realtimeEvent) {
        return toPollEventDto(realtimeEvent);
    }
    
    /**
     * Map PollEventDto.EventType to unified EventType
     */
    private static EventType mapPollEventTypeToEventType(PollEventDto.EventType pollType) {
        if (pollType == null) {
            return EventType.MESSAGE;
        }
        
        return switch (pollType) {
            case MESSAGE -> EventType.MESSAGE;
            case MESSAGE_EDIT -> EventType.MESSAGE_EDIT;
            case MESSAGE_DELETE -> EventType.MESSAGE_DELETE;
            case REACTION -> EventType.REACTION;
        };
    }
    
    /**
     * Map unified EventType back to PollEventDto.EventType
     */
    private static PollEventDto.EventType mapEventTypeToPollEventType(EventType eventType) {
        if (eventType == null) {
            return PollEventDto.EventType.MESSAGE;
        }
        
        return switch (eventType) {
            case MESSAGE, MESSAGE_FILE -> PollEventDto.EventType.MESSAGE;
            case MESSAGE_EDIT -> PollEventDto.EventType.MESSAGE_EDIT;
            case MESSAGE_DELETE -> PollEventDto.EventType.MESSAGE_DELETE;
            case REACTION, REACTION_ADD, REACTION_REMOVE -> PollEventDto.EventType.REACTION;
            // Future event types default to MESSAGE for now
            default -> PollEventDto.EventType.MESSAGE;
        };
    }
}
