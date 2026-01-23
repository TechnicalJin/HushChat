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
public class OtpSession {
    private String deviceId;
    private String otp;
    private LocalDateTime expiryTime;
    private int retryCount;
    private LocalDateTime createdAt;
    
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiryTime);
    }
    
    public boolean hasRetriesLeft(int maxRetries) {
        return retryCount < maxRetries;
    }
}