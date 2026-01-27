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
public class FileUploadResponseDto {
    private String fileId;
    private String roomCode;
    private String uploaderId;
    private String uploaderName;
    private String originalFilename;
    private String contentType;
    private long fileSize;
    private String downloadUrl;
    private LocalDateTime uploadTime;
    private LocalDateTime expiryTime;
    
    /**
     * Check if file is an image based on content type.
     */
    public boolean isImage() {
        return contentType != null && contentType.startsWith("image/");
    }
    
    /**
     * Get human-readable file size.
     */
    public String getFileSizeFormatted() {
        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.1f KB", fileSize / 1024.0);
        } else if (fileSize < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", fileSize / (1024.0 * 1024));
        } else {
            return String.format("%.1f GB", fileSize / (1024.0 * 1024 * 1024));
        }
    }
}
