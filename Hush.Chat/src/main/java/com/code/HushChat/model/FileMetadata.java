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
public class FileMetadata {
    private String fileId;
    private String roomCode;
    private String userId;
    private String originalFilename;
    private String storedFilename;
    private String contentType;
    private long fileSize;
    private String filePath;
    private LocalDateTime uploadTime;
    private LocalDateTime expiryTime;
    
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiryTime);
    }
}
