package com.code.HushChat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO for reaction responses
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReactionResponseDto {
    
    private String reactionId;
    private String messageId;
    private String roomCode;
    private String userId;
    private String userName;
    private String emoji;
    private LocalDateTime createdAt;
    private String action; // "added" or "removed" for real-time updates
    
    /**
     * Aggregated reaction summary for a message
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReactionSummary {
        private String messageId;
        private Map<String, EmojiCount> reactions; // emoji -> count and users
    }
    
    /**
     * Count and user info for each emoji type
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmojiCount {
        private String emoji;
        private int count;
        private List<String> userIds;     // List of user IDs who reacted
        private List<String> userNames;   // List of user names who reacted
    }
}
