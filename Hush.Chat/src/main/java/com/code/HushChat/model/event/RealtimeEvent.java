package com.code.HushChat.model.event;

import java.time.LocalDateTime;

/**
 * Base interface for all real-time events in Hush.Chat.
 * Provides a unified event model for WebSocket-based real-time communication.
 * 
 * NOTE: Long polling support intentionally removed. WebSocket is the ONLY transport.
 * 
 * @since 2.0.0 (WebSocket-only version)
 */
public interface RealtimeEvent {
    
    /**
     * Get the event type discriminator
     * @return EventType enum value
     */
    EventType getEventType();
    
    /**
     * Get the timestamp when this event occurred
     * @return LocalDateTime of event creation
     */
    LocalDateTime getTimestamp();
    
    /**
     * Get the room code where this event occurred (if applicable)
     * @return Room code or null for global events
     */
    String getRoomCode();
    
    /**
     * Get the event payload/data
     * This can be a DTO, domain model, or simple data structure
     * @return Event payload object
     */
    Object getPayload();
    
    /**
     * Get a unique identifier for this event (optional)
     * Useful for event deduplication and tracking
     * @return Event ID or null
     */
    default String getEventId() {
        return null;
    }
    
    /**
     * Check if this event should be broadcast to all room members
     * @return true if broadcast, false if targeted to specific users
     */
    default boolean isBroadcast() {
        return true;
    }
}
