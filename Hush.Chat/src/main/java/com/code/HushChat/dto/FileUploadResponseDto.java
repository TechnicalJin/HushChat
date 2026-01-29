package com.code.HushChat.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class FileUploadResponseDto {
    private String fileId;
    private String roomCode;
    private String senderId;
    private String senderName;
    private String originalFilename;
    private String contentType;
    private long fileSize;
    private String downloadUrl;
    private LocalDateTime uploadTime;
    private LocalDateTime expiryTime;
}


