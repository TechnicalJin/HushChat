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
    
    public enum MessageType {
        TEXT, FILE, SYSTEM
    }
    
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiryTime);
    }
}