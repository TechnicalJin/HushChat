package com.code.HushChat.storage;

import com.code.HushChat.model.ChatRoom;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryRoomStore {
    
    private final Map<String, ChatRoom> roomStore = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> userJoinTimes = new ConcurrentHashMap<>();
    
    public void save(String roomCode, ChatRoom room) {
        roomStore.put(roomCode, room);
    }
    
    public ChatRoom get(String roomCode) {
        return roomStore.get(roomCode);
    }
    
    public void delete(String roomCode) {
        roomStore.remove(roomCode);
        // Clean up join times for this room
        userJoinTimes.entrySet().removeIf(entry -> 
            entry.getKey().startsWith(roomCode + ":"));
    }
    
    public Map<String, ChatRoom> getAll() {
        return new ConcurrentHashMap<>(roomStore);
    }
    
    public void clear() {
        roomStore.clear();
        userJoinTimes.clear();
    }
    
    public int size() {
        return roomStore.size();
    }
    
    // Track when users join rooms (for message visibility)
    public void recordUserJoinTime(String roomCode, String userId) {
        String key = roomCode + ":" + userId;
        userJoinTimes.put(key, LocalDateTime.now());
    }
    
    public LocalDateTime getUserJoinTime(String roomCode, String userId) {
        String key = roomCode + ":" + userId;
        return userJoinTimes.get(key);
    }
    
    public void removeUserJoinTime(String roomCode, String userId) {
        String key = roomCode + ":" + userId;
        userJoinTimes.remove(key);
    }
}