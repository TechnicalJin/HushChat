package com.code.HushChat.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    private String messageId;
    private String roomCode;
    private String userId;
    private String content;
    private MessageType type;
    private LocalDateTime timestamp;
    private LocalDateTime expiryTime;
    private String fileId; // Optional, for file messages
    private boolean edited; // True if message was edited
    private boolean deleted; // True if message was unsent/deleted
    private LocalDateTime lastModified; // Track when message was last modified
    
    // Reply support - stores minimal reference to the original message
    private ReplyTo replyTo;
    
    public enum MessageType {
        TEXT, FILE, SYSTEM
    }
    
    /**
     * Embedded reply metadata - stores only essential info to avoid duplicating full message
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReplyTo {
        private String messageId;      // Original message ID
        private String senderId;       // Original sender ID
        private String senderName;     // Original sender name
        private String messageType;    // TEXT, FILE, etc.
        private String previewText;    // Truncated preview (max 100 chars)
        private boolean deleted;       // True if original message was deleted
    }
    
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiryTime);
    }
}