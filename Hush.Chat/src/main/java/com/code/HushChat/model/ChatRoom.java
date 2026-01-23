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
    private String roomName;
    private String creatorId;
    private Set<String> activeUserIds;
    private ConcurrentHashMap<String, String> userIdToName; // userId -> userName mapping
    private int maxUsers;
    private LocalDateTime createdAt;
    private LocalDateTime lastActivity;
    private LocalDateTime expiryTime;
    
    public ChatRoom(String roomCode, String roomName, String creatorId, String creatorName, int maxUsers, int inactivityTimeoutMinutes) {
        this.roomCode = roomCode;
        this.roomName = roomName;
        this.creatorId = creatorId;
        this.maxUsers = maxUsers;
        this.activeUserIds = ConcurrentHashMap.newKeySet();
        this.activeUserIds.add(creatorId);
        this.userIdToName = new ConcurrentHashMap<>();
        this.userIdToName.put(creatorId, creatorName);
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
    
    public boolean addUser(String userId, String userName) {
        if (isFull()) {
            return false;
        }
        boolean added = activeUserIds.add(userId);
        if (added) {
            userIdToName.put(userId, userName);
        }
        return added;
    }
    
    public void removeUser(String userId) {
        activeUserIds.remove(userId);
        userIdToName.remove(userId);
    }
    
    public Set<String> getUserNames() {
        return ConcurrentHashMap.newKeySet(userIdToName.values().size()).addAll(userIdToName.values()) 
            ? userIdToName.keySet() 
            : Set.copyOf(userIdToName.values());
    }
}