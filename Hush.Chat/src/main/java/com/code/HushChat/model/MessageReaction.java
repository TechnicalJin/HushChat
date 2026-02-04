package com.code.HushChat.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents a reaction (emoji) on a message.
 * Each user can add one reaction per emoji type per message.
 * Uniqueness: (messageId, userId, emoji)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageReaction {
    
    private String reactionId;
    private String messageId;
    private String roomCode;
    private String userId;
    private String userName;
    private String emoji;        // The emoji character (e.g., "❤️", "😂", "👍")
    private LocalDateTime createdAt;
    
    /**
     * Create a unique key for this reaction to prevent duplicates
     * Format: messageId:userId:emoji
     */
    public String getUniqueKey() {
        return messageId + ":" + userId + ":" + emoji;
    }
}
