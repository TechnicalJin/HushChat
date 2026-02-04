package com.code.HushChat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO for WebSocket reaction events.
 * Broadcasted to all users in a room when reactions change.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReactionEventDto {
    
    /**
     * Event type: "REACTION_UPDATE"
     */
    private String type;
    
    /**
     * The message that was reacted to
     */
    private String messageId;
    
    /**
     * The room code
     */
    private String roomCode;
    
    /**
     * The emoji used for the reaction
     */
    private String emoji;
    
    /**
     * The user who triggered the reaction
     */
    private String userId;
    
    /**
     * The user's display name
     */
    private String userName;
    
    /**
     * Action: "ADD" or "REMOVE"
     */
    private String action;
    
    /**
     * Timestamp of the event
     */
    private LocalDateTime timestamp;
    
    /**
     * Updated reaction summary for the message.
     * Contains all reactions for quick UI update.
     */
    private Map<String, EmojiReactionInfo> reactions;
    
    /**
     * Information about a specific emoji reaction
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmojiReactionInfo {
        private int count;
        private List<String> userIds;
        private List<String> userNames;
    }
}
