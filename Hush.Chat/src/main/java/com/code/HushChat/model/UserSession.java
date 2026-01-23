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
public class UserSession {
    private String sessionToken;
    private String deviceId;
    private String userId;
    private LocalDateTime createdAt;
    private LocalDateTime expiryTime;
    private LocalDateTime lastActivity;
    
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiryTime);
    }
    
    public void updateActivity() {
        this.lastActivity = LocalDateTime.now();
    }
}