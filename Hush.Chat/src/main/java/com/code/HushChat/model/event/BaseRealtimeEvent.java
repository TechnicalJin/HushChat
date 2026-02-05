package com.code.HushChat.model.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Base implementation of RealtimeEvent.
 * Provides common fields and utility methods for all event types.
 * 
 * Can be extended or used directly with a generic payload.
 * 
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BaseRealtimeEvent implements RealtimeEvent {
    
    /**
     * Unique event identifier (auto-generated if not provided)
     */
    @Builder.Default
    private String eventId = UUID.randomUUID().toString();
    
    /**
     * Event type discriminator
     */
    private EventType eventType;
    
    /**
     * Timestamp when event occurred
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    
    /**
     * Room code where event occurred (nullable for global events)
     */
    private String roomCode;
    
    /**
     * Event payload/data (DTO, domain model, etc.)
     */
    private Object payload;
    
    /**
     * Whether to broadcast to all room members
     */
    @Builder.Default
    private boolean broadcast = true;
    
    /**
     * Optional: User ID who triggered this event
     */
    private String triggeredBy;
    
    /**
     * Optional: Target user IDs (if not broadcast)
     */
    private java.util.List<String> targetUserIds;
    
    // ========== FACTORY METHODS ==========
    
    /**
     * Create a message event
     */
    public static BaseRealtimeEvent messageEvent(String roomCode, Object messagePayload) {
        return BaseRealtimeEvent.builder()
                .eventType(EventType.MESSAGE)
                .roomCode(roomCode)
                .payload(messagePayload)
                .broadcast(true)
                .build();
    }
    
    /**
     * Create a reaction event
     */
    public static BaseRealtimeEvent reactionEvent(String roomCode, Object reactionPayload) {
        return BaseRealtimeEvent.builder()
                .eventType(EventType.REACTION)
                .roomCode(roomCode)
                .payload(reactionPayload)
                .broadcast(true)
                .build();
    }
    
    /**
     * Create a user join event
     */
    public static BaseRealtimeEvent userJoinEvent(String roomCode, String userId, Object userPayload) {
        return BaseRealtimeEvent.builder()
                .eventType(EventType.USER_JOIN)
                .roomCode(roomCode)
                .payload(userPayload)
                .triggeredBy(userId)
                .broadcast(true)
                .build();
    }
    
    /**
     * Create a user leave event
     */
    public static BaseRealtimeEvent userLeaveEvent(String roomCode, String userId, Object userPayload) {
        return BaseRealtimeEvent.builder()
                .eventType(EventType.USER_LEAVE)
                .roomCode(roomCode)
                .payload(userPayload)
                .triggeredBy(userId)
                .broadcast(true)
                .build();
    }
    
    /**
     * Create a custom event
     */
    public static BaseRealtimeEvent customEvent(EventType eventType, String roomCode, Object payload) {
        return BaseRealtimeEvent.builder()
                .eventType(eventType)
                .roomCode(roomCode)
                .payload(payload)
                .broadcast(true)
                .build();
    }
}
