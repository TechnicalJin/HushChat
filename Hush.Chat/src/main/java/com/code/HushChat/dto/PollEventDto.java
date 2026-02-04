package com.code.HushChat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Unified event DTO for long-polling responses.
 * Supports both message events and reaction events.
 * 
 * Event types:
 * - MESSAGE: New message sent
 * - MESSAGE_EDIT: Message was edited
 * - MESSAGE_DELETE: Message was deleted/unsent
 * - REACTION: Reaction added or removed
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PollEventDto {
    
    /**
     * Event type discriminator
     */
    public enum EventType {
        MESSAGE,        // New message
        MESSAGE_EDIT,   // Message edited
        MESSAGE_DELETE, // Message deleted/unsent
        REACTION        // Reaction added/removed
    }
    
    /**
     * The type of event
     */
    private EventType type;
    
    /**
     * Room where the event occurred
     */
    private String roomCode;
    
    /**
     * Timestamp of the event
     */
    private LocalDateTime timestamp;
    
    // ============== MESSAGE FIELDS ==============
    
    /**
     * For MESSAGE events: the full message data
     */
    private MessageResponseDto message;
    
    // ============== REACTION FIELDS ==============
    
    /**
     * For REACTION events: the message ID that was reacted to
     */
    private String messageId;
    
    /**
     * For REACTION events: the emoji used
     */
    private String emoji;
    
    /**
     * For REACTION events: the user who triggered the reaction
     */
    private String reactedByUserId;
    
    /**
     * For REACTION events: the user's display name
     */
    private String reactedByUserName;
    
    /**
     * For REACTION events: "added" or "removed"
     */
    private String action;
    
    /**
     * For REACTION events: updated reaction counts for the message.
     * Map of emoji -> reaction info (count, userIds, userNames)
     */
    private Map<String, ReactionInfo> updatedReactionCounts;
    
    /**
     * Reaction info for a specific emoji
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReactionInfo {
        private int count;
        private List<String> userIds;
        private List<String> userNames;
    }
    
    // ============== FACTORY METHODS ==============
    
    /**
     * Create a MESSAGE event
     */
    public static PollEventDto messageEvent(String roomCode, MessageResponseDto message) {
        return PollEventDto.builder()
                .type(EventType.MESSAGE)
                .roomCode(roomCode)
                .timestamp(message.getTimestamp())
                .message(message)
                .build();
    }
    
    /**
     * Create a MESSAGE_EDIT event
     */
    public static PollEventDto messageEditEvent(String roomCode, MessageResponseDto message) {
        return PollEventDto.builder()
                .type(EventType.MESSAGE_EDIT)
                .roomCode(roomCode)
                .timestamp(message.getLastModified() != null ? message.getLastModified() : LocalDateTime.now())
                .message(message)
                .build();
    }
    
    /**
     * Create a MESSAGE_DELETE event
     */
    public static PollEventDto messageDeleteEvent(String roomCode, MessageResponseDto message) {
        return PollEventDto.builder()
                .type(EventType.MESSAGE_DELETE)
                .roomCode(roomCode)
                .timestamp(message.getLastModified() != null ? message.getLastModified() : LocalDateTime.now())
                .message(message)
                .build();
    }
    
    /**
     * Create a REACTION event
     */
    public static PollEventDto reactionEvent(
            String roomCode, 
            String messageId,
            String emoji,
            String reactedByUserId,
            String reactedByUserName,
            String action,
            Map<String, ReactionInfo> updatedReactionCounts) {
        
        return PollEventDto.builder()
                .type(EventType.REACTION)
                .roomCode(roomCode)
                .timestamp(LocalDateTime.now())
                .messageId(messageId)
                .emoji(emoji)
                .reactedByUserId(reactedByUserId)
                .reactedByUserName(reactedByUserName)
                .action(action)
                .updatedReactionCounts(updatedReactionCounts)
                .build();
    }
}
