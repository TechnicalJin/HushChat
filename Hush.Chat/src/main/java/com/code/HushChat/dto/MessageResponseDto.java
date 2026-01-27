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
    private LocalDateTime timestamp;
    private LocalDateTime expiryTime;
    private boolean edited;
    private boolean deleted;
    private LocalDateTime lastModified;
    
    // File-related fields (only populated for FILE type messages)
    private String fileId;
    private String fileName;
    private String fileContentType;
    private Long fileSize;
    private String fileDownloadUrl;
}
