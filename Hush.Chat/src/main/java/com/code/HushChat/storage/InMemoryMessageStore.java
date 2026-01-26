package com.code.HushChat.storage;

import com.code.HushChat.model.ChatMessage;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory storage for chat messages.
 * Thread-safe implementation using ConcurrentHashMap and synchronized lists.
 * 
 * Messages are organized by room code for efficient room-based operations.
 */
@Component
public class InMemoryMessageStore {
    
    // roomCode -> List of messages
    private final Map<String, List<ChatMessage>> messageStore = new ConcurrentHashMap<>();
    
    public void save(String roomCode, ChatMessage message) {
        messageStore.computeIfAbsent(roomCode, k -> 
            Collections.synchronizedList(new ArrayList<>()))
            .add(message);
    }
    
    public List<ChatMessage> getMessages(String roomCode) {
        List<ChatMessage> messages = messageStore.get(roomCode);
        if (messages == null) {
            return Collections.emptyList();
        }
        // Return a copy to prevent concurrent modification issues during iteration
        synchronized (messages) {
            return new ArrayList<>(messages);
        }
    }
    
    public void deleteMessage(String roomCode, String messageId) {
        List<ChatMessage> messages = messageStore.get(roomCode);
        if (messages != null) {
            synchronized (messages) {
                messages.removeIf(msg -> msg.getMessageId().equals(messageId));
            }
        }
    }
    
    public void deleteRoomMessages(String roomCode) {
        messageStore.remove(roomCode);
    }
    
    /**
     * Get a copy of all room-message mappings for safe iteration during cleanup.
     * Note: The returned lists are copies to ensure thread safety.
     * @return copy of all message entries by room
     */
    public Map<String, List<ChatMessage>> getAll() {
        Map<String, List<ChatMessage>> copy = new ConcurrentHashMap<>();
        messageStore.forEach((roomCode, messages) -> {
            synchronized (messages) {
                copy.put(roomCode, new ArrayList<>(messages));
            }
        });
        return copy;
    }
    
    /**
     * Remove expired messages from a specific room.
     * Thread-safe removal using synchronized access.
     * @param roomCode the room to clean
     * @return number of messages removed
     */
    public int removeExpiredMessages(String roomCode) {
        List<ChatMessage> messages = messageStore.get(roomCode);
        if (messages == null) {
            return 0;
        }
        
        int removed = 0;
        synchronized (messages) {
            int sizeBefore = messages.size();
            messages.removeIf(ChatMessage::isExpired);
            removed = sizeBefore - messages.size();
            
            // Clean up empty room entry
            if (messages.isEmpty()) {
                messageStore.remove(roomCode);
            }
        }
        return removed;
    }
    
    /**
     * Remove all expired messages from all rooms.
     * @return total number of messages removed
     */
    public int removeAllExpiredMessages() {
        int totalRemoved = 0;
        // Get room codes first to avoid concurrent modification
        Set<String> roomCodes = new HashSet<>(messageStore.keySet());
        
        for (String roomCode : roomCodes) {
            totalRemoved += removeExpiredMessages(roomCode);
        }
        
        return totalRemoved;
    }
    
    public void clear() {
        messageStore.clear();
    }
    
    public int getTotalMessageCount() {
        return messageStore.values().stream()
            .mapToInt(List::size)
            .sum();
    }
    
    public int getRoomMessageCount(String roomCode) {
        List<ChatMessage> messages = messageStore.get(roomCode);
        return messages != null ? messages.size() : 0;
    }
    
    /**
     * Check if a room has any messages.
     * @param roomCode the room code
     * @return true if room has messages
     */
    public boolean hasMessagesForRoom(String roomCode) {
        List<ChatMessage> messages = messageStore.get(roomCode);
        return messages != null && !messages.isEmpty();
    }
    
    /**
     * Get the count of rooms with messages.
     * @return number of rooms with messages
     */
    public int getRoomCount() {
        return messageStore.size();
    }
}