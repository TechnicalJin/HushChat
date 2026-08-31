package com.code.HushChat.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoom {
    private String roomCode;
    private String roomName;
    private String creatorId;
    private Set<String> activeUserIds;
    private Set<String> inactiveUserIds;
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
        this.inactiveUserIds = ConcurrentHashMap.newKeySet();
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
            inactiveUserIds.remove(userId);
            userIdToName.put(userId, userName);
        }
        return added;
    }

    public boolean hasUserMembership(String userId) {
        return userIdToName.containsKey(userId) || inactiveUserIds.contains(userId);
    }

    public boolean isUserActive(String userId) {
        return activeUserIds.contains(userId);
    }

    public boolean reactivateUser(String userId, String userName) {
        if (isFull() && !activeUserIds.contains(userId)) {
            return false;
        }

        if (activeUserIds.add(userId)) {
            inactiveUserIds.remove(userId);
            if (userName != null && !userName.isBlank()) {
                userIdToName.put(userId, userName);
            }
            return true;
        }

        if (userName != null && !userName.isBlank()) {
            userIdToName.put(userId, userName);
        }
        return true;
    }
    
    public boolean removeUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }

        boolean removed = activeUserIds.remove(userId);
        if (!removed) {
            return false;
        }

        if (userIdToName.containsKey(userId)) {
            inactiveUserIds.add(userId);
        }
        return true;
    }
    
    public Set<String> getUserNames() {
        return activeUserIds.stream()
            .map(userIdToName::get)
            .filter(name -> name != null && !name.isBlank())
            .collect(Collectors.toSet());
    }
}