package com.code.HushChat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponseDto {
    private String messageId;
    private String roomCode;
    private String senderId;
    private String senderName;
    private String content;
    private String type;
    private String fileId; // Optional: for file messages
    private String downloadUrl; // Optional: for file messages - download URL
    private LocalDateTime timestamp;
    private LocalDateTime expiryTime;
    private boolean edited;
    private boolean deleted;
    private LocalDateTime lastModified;
    
    // Reply metadata - minimal reference to original message
    private ReplyToDto replyTo;
    
    /**
     * Reply metadata DTO - contains only essential info about the replied message
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReplyToDto {
        private String messageId;      // Original message ID for scroll-to functionality
        private String senderId;       // Original sender ID
        private String senderName;     // Original sender name for display
        private String messageType;    // TEXT, FILE, etc. for icon display
        private String previewText;    // Truncated preview text (max 100 chars)
        private boolean deleted;       // True if original message was deleted/unsent
    }
}
