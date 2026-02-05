package com.code.HushChat.model.event;

/**
 * Enumeration of all real-time event types in Hush.Chat.
 * Used by both Long Polling and WebSocket implementations.
 * 
 * Event categories:
 * - MESSAGE_*: Message lifecycle events
 * - REACTION_*: Emoji reaction events
 * - USER_*: User presence events
 * - ROOM_*: Room lifecycle events
 * - SYSTEM_*: System notifications
 * 
 * @since 1.0.0
 */
public enum EventType {
    
    // ========== MESSAGE EVENTS ==========
    
    /**
     * New message sent to room
     */
    MESSAGE,
    
    /**
     * Existing message edited/updated
     */
    MESSAGE_EDIT,
    
    /**
     * Message deleted/unsent
     */
    MESSAGE_DELETE,
    
    /**
     * File attachment uploaded
     */
    MESSAGE_FILE,
    
    // ========== REACTION EVENTS ==========
    
    /**
     * Emoji reaction added to message
     */
    REACTION_ADD,
    
    /**
     * Emoji reaction removed from message
     */
    REACTION_REMOVE,
    
    /**
     * Generic reaction event (add or remove)
     * Legacy support for current PollEventDto.REACTION
     */
    REACTION,
    
    // ========== USER EVENTS ==========
    
    /**
     * User joined the room
     */
    USER_JOIN,
    
    /**
     * User left the room
     */
    USER_LEAVE,
    
    /**
     * User is typing indicator
     */
    USER_TYPING,
    
    /**
     * User presence update (online/offline)
     */
    USER_PRESENCE,
    
    // ========== ROOM EVENTS ==========
    
    /**
     * Room created
     */
    ROOM_CREATE,
    
    /**
     * Room closed/deleted
     */
    ROOM_CLOSE,
    
    /**
     * Room settings updated
     */
    ROOM_UPDATE,
    
    // ========== SYSTEM EVENTS ==========
    
    /**
     * System notification/announcement
     */
    SYSTEM_NOTIFICATION,
    
    /**
     * Error event
     */
    SYSTEM_ERROR,
    
    /**
     * Connection established (WebSocket only)
     */
    SYSTEM_CONNECT,
    
    /**
     * Connection closed (WebSocket only)
     */
    SYSTEM_DISCONNECT
}
