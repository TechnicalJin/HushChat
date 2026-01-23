package com.code.HushChat.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoom {
    private String roomCode;
    private String creatorId;
    private Set<String> activeUserIds;
    private int maxUsers;
    private LocalDateTime createdAt;
    private LocalDateTime lastActivity;
    private LocalDateTime expiryTime;
    
    public ChatRoom(String roomCode, String creatorId, int maxUsers, int inactivityTimeoutMinutes) {
        this.roomCode = roomCode;
        this.creatorId = creatorId;
        this.maxUsers = maxUsers;
        this.activeUserIds = ConcurrentHashMap.newKeySet();
        this.activeUserIds.add(creatorId);
        this.createdAt = LocalDateTime.now();
        this.lastActivity = LocalDateTime.now();
        this.expiryTime = LocalDateTime.now().plusMinutes(inactivityTimeoutMinutes);
    }
    
    public boolean isFull() {
        return activeUserIds.size() >= maxUsers;
    }
    
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiryTime);
    }
    
    public void updateActivity(int inactivityTimeoutMinutes) {
        this.lastActivity = LocalDateTime.now();
        this.expiryTime = LocalDateTime.now().plusMinutes(inactivityTimeoutMinutes);
    }
    
    public boolean addUser(String userId) {
        if (isFull()) {
            return false;
        }
        return activeUserIds.add(userId);
    }
    
    public void removeUser(String userId) {
        activeUserIds.remove(userId);
    }
}