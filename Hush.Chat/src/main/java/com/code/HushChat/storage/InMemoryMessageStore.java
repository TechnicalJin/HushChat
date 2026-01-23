package com.code.HushChat.storage;

import com.code.HushChat.model.ChatMessage;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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
        return messageStore.getOrDefault(roomCode, Collections.emptyList());
    }
    
    public void deleteMessage(String roomCode, String messageId) {
        List<ChatMessage> messages = messageStore.get(roomCode);
        if (messages != null) {
            messages.removeIf(msg -> msg.getMessageId().equals(messageId));
        }
    }
    
    public void deleteRoomMessages(String roomCode) {
        messageStore.remove(roomCode);
    }
    
    public Map<String, List<ChatMessage>> getAll() {
        return new ConcurrentHashMap<>(messageStore);
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
        return messageStore.getOrDefault(roomCode, Collections.emptyList()).size();
    }
}