package com.code.HushChat.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory presence tracking service.
 * 
 * PRESENCE RULE:
 * User is ACTIVE if and only if:
 *   - WebSocket connected (connected = true)
 *   - AND tab is visible (visible = true)
 * 
 * Otherwise: INACTIVE
 * 
 * Data model:
 * roomCode -> userId -> PresenceState { connected, visible }
 * 
 * Thread-safe: Uses ConcurrentHashMap
 * 
 * @since 1.0.0
 */
@Service
@Slf4j
public class PresenceService {
    
    // roomCode -> (userId -> PresenceState)
    private final Map<String, Map<String, PresenceState>> presenceByRoom = new ConcurrentHashMap<>();
    
    /**
     * Update user connection state.
     * Called when WebSocket connects/disconnects.
     * 
     * @param roomCode Room code
     * @param userId User ID
     * @param connected true if WebSocket connected
     */
    public void updateConnection(String roomCode, String userId, boolean connected) {
        if (roomCode == null || userId == null) {
            log.warn("Cannot update connection: roomCode or userId is null");
            return;
        }
        
        Map<String, PresenceState> roomPresence = presenceByRoom.computeIfAbsent(
            roomCode, k -> new ConcurrentHashMap<>()
        );
        
        PresenceState state = roomPresence.computeIfAbsent(
            userId, k -> new PresenceState(false, false)
        );
        
        state.setConnected(connected);
        
        log.debug("Connection updated: roomCode={}, userId={}, connected={}, isActive={}", 
                  roomCode, userId, connected, state.isActive());
    }
    
    /**
     * Update user visibility state (tab visible/hidden).
     * Called when browser tab visibility changes.
     * 
     * @param roomCode Room code
     * @param userId User ID
     * @param visible true if tab is visible
     */
    public void updateVisibility(String roomCode, String userId, boolean visible) {
        if (roomCode == null || userId == null) {
            log.warn("Cannot update visibility: roomCode or userId is null");
            return;
        }
        
        Map<String, PresenceState> roomPresence = presenceByRoom.computeIfAbsent(
            roomCode, k -> new ConcurrentHashMap<>()
        );
        
        PresenceState state = roomPresence.computeIfAbsent(
            userId, k -> new PresenceState(false, false)
        );
        
        state.setVisible(visible);
        
        log.debug("Visibility updated: roomCode={}, userId={}, visible={}, isActive={}", 
                  roomCode, userId, visible, state.isActive());
    }
    
    /**
     * Check if user is currently active.
     * 
     * @param roomCode Room code
     * @param userId User ID
     * @return true if user is active (connected AND visible)
     */
    public boolean isUserActive(String roomCode, String userId) {
        if (roomCode == null || userId == null) {
            return false;
        }
        
        Map<String, PresenceState> roomPresence = presenceByRoom.get(roomCode);
        if (roomPresence == null) {
            return false;
        }
        
        PresenceState state = roomPresence.get(userId);
        return state != null && state.isActive();
    }
    
    /**
     * Get all active users in a room.
     * 
     * @param roomCode Room code
     * @return Set of active user IDs
     */
    public Set<String> getActiveUsers(String roomCode) {
        if (roomCode == null) {
            return Collections.emptySet();
        }
        
        Map<String, PresenceState> roomPresence = presenceByRoom.get(roomCode);
        if (roomPresence == null) {
            return Collections.emptySet();
        }
        
        return roomPresence.entrySet().stream()
            .filter(entry -> entry.getValue().isActive())
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
    }
    
    /**
     * Get presence status for all users in a room.
     * Returns map of userId -> isActive
     * 
     * @param roomCode Room code
     * @return Map of userId to presence status
     */
    public Map<String, Boolean> getRoomPresence(String roomCode) {
        if (roomCode == null) {
            return Collections.emptyMap();
        }
        
        Map<String, PresenceState> roomPresence = presenceByRoom.get(roomCode);
        if (roomPresence == null) {
            return Collections.emptyMap();
        }
        
        return roomPresence.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().isActive()
            ));
    }
    
    /**
     * Remove user from all presence tracking.
     * Called on WebSocket disconnect.
     * 
     * @param roomCode Room code
     * @param userId User ID
     */
    public void removeUser(String roomCode, String userId) {
        if (roomCode == null || userId == null) {
            return;
        }
        
        Map<String, PresenceState> roomPresence = presenceByRoom.get(roomCode);
        if (roomPresence != null) {
            roomPresence.remove(userId);
            
            // Clean up empty room maps
            if (roomPresence.isEmpty()) {
                presenceByRoom.remove(roomCode);
            }
            
            log.debug("User removed from presence: roomCode={}, userId={}", roomCode, userId);
        }
    }
    
    /**
     * Get active user count in a room.
     * 
     * @param roomCode Room code
     * @return Number of active users
     */
    public int getActiveUserCount(String roomCode) {
        return getActiveUsers(roomCode).size();
    }
    
    /**
     * Internal presence state holder.
     */
    @Data
    @AllArgsConstructor
    private static class PresenceState {
        private boolean connected;  // WebSocket connection state
        private boolean visible;    // Browser tab visibility
        
        /**
         * User is active if BOTH connected AND visible.
         */
        public boolean isActive() {
            return connected && visible;
        }
    }
}
