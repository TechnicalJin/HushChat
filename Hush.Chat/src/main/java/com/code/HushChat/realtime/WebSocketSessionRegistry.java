package com.code.HushChat.realtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Registry for tracking active WebSocket sessions.
 * Maps users and rooms to their WebSocket session IDs.
 * 
 * Thread-safety: Uses ConcurrentHashMap and CopyOnWriteArraySet for concurrent access.
 * 
 * Responsibilities:
 * - Track which users are connected via WebSocket
 * - Track which rooms each user is subscribed to
 * - Map userId -> Set of session IDs (supports multiple tabs/devices)
 * - Map roomCode -> Set of user IDs (who's in this room via WebSocket)
 * - Handle connect/disconnect events
 * - Provide session lookup for targeted message delivery
 * 
 * @since 1.0.0
 */
@Component
@Slf4j
public class WebSocketSessionRegistry {
    
    /**
     * Maps userId to their active WebSocket session IDs.
     * Supports multiple sessions per user (multiple tabs/devices).
     */
    private final Map<String, Set<String>> userToSessions = new ConcurrentHashMap<>();
    
    /**
     * Maps WebSocket session ID to userId.
     * Used for quick lookup during disconnect.
     */
    private final Map<String, String> sessionToUser = new ConcurrentHashMap<>();
    
    /**
     * Maps WebSocket session ID to roomCode.
     * Used for cleanup during disconnect.
     */
    private final Map<String, String> sessionToRoom = new ConcurrentHashMap<>();
    
    /**
     * Maps roomCode to set of user IDs currently connected via WebSocket.
     */
    private final Map<String, Set<String>> roomToUsers = new ConcurrentHashMap<>();
    
    /**
     * Register a new WebSocket session.
     * 
     * @param sessionId WebSocket session ID (STOMP session ID)
     * @param userId User ID who owns this session
     * @param roomCode Room code user is joining
     */
    public void registerSession(String sessionId, String userId, String roomCode) {
        if (sessionId == null || userId == null || roomCode == null) {
            log.warn("Cannot register session with null values: sessionId={}, userId={}, roomCode={}", 
                     sessionId, userId, roomCode);
            return;
        }
        
        log.info("Registering WebSocket session: sessionId={}, userId={}, roomCode={}", 
                 sessionId, userId, roomCode);
        
        // Map sessionId -> userId
        sessionToUser.put(sessionId, userId);
        
        // Map sessionId -> roomCode
        sessionToRoom.put(sessionId, roomCode);
        
        // Add session to user's session set (supports multiple sessions per user)
        userToSessions.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>())
                      .add(sessionId);
        
        // Add user to room's user set
        roomToUsers.computeIfAbsent(roomCode, k -> new CopyOnWriteArraySet<>())
                   .add(userId);
        
        log.debug("Session registered successfully. User {} now has {} active session(s) in room {}", 
                  userId, userToSessions.get(userId).size(), roomCode);
    }
    
    /**
     * Unregister a WebSocket session (on disconnect).
     * Cleans up all mappings for this session.
     * 
     * @param sessionId WebSocket session ID to remove
     */
    public void unregisterSession(String sessionId) {
        if (sessionId == null) {
            log.warn("Cannot unregister null sessionId");
            return;
        }
        
        log.info("Unregistering WebSocket session: {}", sessionId);
        
        // Look up userId and roomCode
        String userId = sessionToUser.remove(sessionId);
        String roomCode = sessionToRoom.remove(sessionId);
        
        if (userId == null) {
            log.debug("Session {} not found in registry (already cleaned up or never registered)", sessionId);
            return;
        }
        
        // Remove session from user's session set
        Set<String> sessions = userToSessions.get(userId);
        if (sessions != null) {
            sessions.remove(sessionId);
            
            // Clean up empty user entry
            if (sessions.isEmpty()) {
                userToSessions.remove(userId);
                log.debug("User {} has no more active WebSocket sessions", userId);
            } else {
                log.debug("User {} still has {} active session(s)", userId, sessions.size());
            }
        }
        
        // Remove user from room if no more sessions
        if (roomCode != null && !isUserConnected(userId)) {
            Set<String> usersInRoom = roomToUsers.get(roomCode);
            if (usersInRoom != null) {
                usersInRoom.remove(userId);
                
                // Clean up empty room entry
                if (usersInRoom.isEmpty()) {
                    roomToUsers.remove(roomCode);
                    log.debug("Room {} has no more WebSocket users", roomCode);
                }
            }
        }
        
        log.info("Session unregistered: sessionId={}, userId={}, roomCode={}", 
                 sessionId, userId, roomCode);
    }
    
    /**
     * Get all session IDs for a specific user.
     * 
     * @param userId User ID to query
     * @return Set of session IDs, empty if none
     */
    public Set<String> getSessionIdsForUser(String userId) {
        if (userId == null) {
            return Set.of();
        }
        
        Set<String> sessions = userToSessions.get(userId);
        return sessions != null ? Set.copyOf(sessions) : Set.of();
    }
    
    /**
     * Get all user IDs currently connected to a room via WebSocket.
     * 
     * @param roomCode Room code to query
     * @return Set of user IDs, empty if none
     */
    public Set<String> getUserIdsInRoom(String roomCode) {
        if (roomCode == null) {
            return Set.of();
        }
        
        Set<String> users = roomToUsers.get(roomCode);
        return users != null ? Set.copyOf(users) : Set.of();
    }
    
    /**
     * Check if a user is currently connected via WebSocket.
     * 
     * @param userId User ID to check
     * @return true if user has at least one active session
     */
    public boolean isUserConnected(String userId) {
        if (userId == null) {
            return false;
        }
        
        Set<String> sessions = userToSessions.get(userId);
        return sessions != null && !sessions.isEmpty();
    }
    
    /**
     * Get the room code for a specific session.
     * 
     * @param sessionId Session ID to query
     * @return Room code, or null if session not found
     */
    public String getRoomCodeForSession(String sessionId) {
        return sessionToRoom.get(sessionId);
    }
    
    /**
     * Get the user ID for a specific session.
     * 
     * @param sessionId Session ID to query
     * @return User ID, or null if session not found
     */
    public String getUserIdForSession(String sessionId) {
        return sessionToUser.get(sessionId);
    }
    
    /**
     * Get total count of active WebSocket sessions.
     * 
     * @return Total number of active sessions
     */
    public int getTotalSessionCount() {
        return sessionToUser.size();
    }
    
    /**
     * Get count of active users across all rooms.
     * 
     * @return Number of unique users connected via WebSocket
     */
    public int getActiveUserCount() {
        return userToSessions.size();
    }
    
    /**
     * Get count of rooms with active WebSocket connections.
     * 
     * @return Number of rooms with at least one WebSocket user
     */
    public int getActiveRoomCount() {
        return roomToUsers.size();
    }
}