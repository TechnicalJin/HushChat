package com.code.HushChat.storage;

import com.code.HushChat.model.MessageReaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory storage for message reactions.
 * Stores reactions with unique constraint on (messageId, userId, emoji).
 */
@Component
@Slf4j
public class InMemoryReactionStore {
    
    // Primary storage: roomCode -> messageId -> List of reactions
    private final Map<String, Map<String, List<MessageReaction>>> roomReactions = new ConcurrentHashMap<>();
    
    // Index for quick lookup by unique key: roomCode -> uniqueKey -> reaction
    private final Map<String, Map<String, MessageReaction>> reactionIndex = new ConcurrentHashMap<>();
    
    /**
     * Add a reaction to a message
     * @return true if reaction was added, false if it already exists
     */
    public synchronized boolean addReaction(MessageReaction reaction) {
        String roomCode = reaction.getRoomCode();
        String uniqueKey = reaction.getUniqueKey();
        
        // Check if reaction already exists
        Map<String, MessageReaction> roomIndex = reactionIndex.computeIfAbsent(roomCode, k -> new ConcurrentHashMap<>());
        if (roomIndex.containsKey(uniqueKey)) {
            log.debug("Reaction already exists: {}", uniqueKey);
            return false;
        }
        
        // Add to primary storage
        Map<String, List<MessageReaction>> messageReactions = roomReactions.computeIfAbsent(roomCode, k -> new ConcurrentHashMap<>());
        List<MessageReaction> reactions = messageReactions.computeIfAbsent(reaction.getMessageId(), k -> Collections.synchronizedList(new ArrayList<>()));
        reactions.add(reaction);
        
        // Add to index
        roomIndex.put(uniqueKey, reaction);
        
        log.debug("Added reaction: {} to message: {}", reaction.getEmoji(), reaction.getMessageId());
        return true;
    }
    
    /**
     * Remove a reaction from a message
     * @return true if reaction was removed, false if it didn't exist
     */
    public synchronized boolean removeReaction(String roomCode, String messageId, String userId, String emoji) {
        String uniqueKey = messageId + ":" + userId + ":" + emoji;
        
        // Check if reaction exists
        Map<String, MessageReaction> roomIndex = reactionIndex.get(roomCode);
        if (roomIndex == null || !roomIndex.containsKey(uniqueKey)) {
            log.debug("Reaction not found: {}", uniqueKey);
            return false;
        }
        
        // Remove from index
        MessageReaction removed = roomIndex.remove(uniqueKey);
        
        // Remove from primary storage
        Map<String, List<MessageReaction>> messageReactions = roomReactions.get(roomCode);
        if (messageReactions != null) {
            List<MessageReaction> reactions = messageReactions.get(messageId);
            if (reactions != null) {
                reactions.removeIf(r -> r.getUniqueKey().equals(uniqueKey));
            }
        }
        
        log.debug("Removed reaction: {} from message: {}", emoji, messageId);
        return true;
    }
    
    /**
     * Toggle a reaction - add if not exists, remove if exists
     * @return the reaction if added, null if removed
     */
    public synchronized MessageReaction toggleReaction(MessageReaction reaction) {
        String uniqueKey = reaction.getUniqueKey();
        Map<String, MessageReaction> roomIndex = reactionIndex.computeIfAbsent(reaction.getRoomCode(), k -> new ConcurrentHashMap<>());
        
        if (roomIndex.containsKey(uniqueKey)) {
            // Remove existing reaction
            removeReaction(reaction.getRoomCode(), reaction.getMessageId(), reaction.getUserId(), reaction.getEmoji());
            return null;
        } else {
            // Add new reaction
            addReaction(reaction);
            return reaction;
        }
    }
    
    /**
     * Get all reactions for a specific message
     */
    public List<MessageReaction> getReactionsForMessage(String roomCode, String messageId) {
        Map<String, List<MessageReaction>> messageReactions = roomReactions.get(roomCode);
        if (messageReactions == null) {
            return Collections.emptyList();
        }
        
        List<MessageReaction> reactions = messageReactions.get(messageId);
        return reactions != null ? new ArrayList<>(reactions) : Collections.emptyList();
    }
    
    /**
     * Get reactions for multiple messages
     */
    public Map<String, List<MessageReaction>> getReactionsForMessages(String roomCode, List<String> messageIds) {
        Map<String, List<MessageReaction>> result = new HashMap<>();
        
        Map<String, List<MessageReaction>> messageReactions = roomReactions.get(roomCode);
        if (messageReactions == null) {
            return result;
        }
        
        for (String messageId : messageIds) {
            List<MessageReaction> reactions = messageReactions.get(messageId);
            if (reactions != null && !reactions.isEmpty()) {
                result.put(messageId, new ArrayList<>(reactions));
            }
        }
        
        return result;
    }
    
    /**
     * Get grouped reaction counts for a message
     * @return Map of emoji -> list of user IDs
     */
    public Map<String, List<String>> getGroupedReactions(String roomCode, String messageId) {
        List<MessageReaction> reactions = getReactionsForMessage(roomCode, messageId);
        
        return reactions.stream()
                .collect(Collectors.groupingBy(
                        MessageReaction::getEmoji,
                        Collectors.mapping(MessageReaction::getUserId, Collectors.toList())
                ));
    }
    
    /**
     * Check if a user has reacted with a specific emoji
     */
    public boolean hasUserReacted(String roomCode, String messageId, String userId, String emoji) {
        String uniqueKey = messageId + ":" + userId + ":" + emoji;
        Map<String, MessageReaction> roomIndex = reactionIndex.get(roomCode);
        return roomIndex != null && roomIndex.containsKey(uniqueKey);
    }
    
    /**
     * Remove all reactions for a message (when message is deleted)
     */
    public synchronized void removeAllReactionsForMessage(String roomCode, String messageId) {
        Map<String, List<MessageReaction>> messageReactions = roomReactions.get(roomCode);
        if (messageReactions != null) {
            List<MessageReaction> reactions = messageReactions.remove(messageId);
            
            // Also remove from index
            if (reactions != null) {
                Map<String, MessageReaction> roomIndex = reactionIndex.get(roomCode);
                if (roomIndex != null) {
                    for (MessageReaction reaction : reactions) {
                        roomIndex.remove(reaction.getUniqueKey());
                    }
                }
            }
        }
        log.debug("Removed all reactions for message: {}", messageId);
    }
    
    /**
     * Remove all reactions for a room (when room is deleted)
     */
    public synchronized void removeAllReactionsForRoom(String roomCode) {
        roomReactions.remove(roomCode);
        reactionIndex.remove(roomCode);
        log.debug("Removed all reactions for room: {}", roomCode);
    }
    
    /**
     * Get total reaction count for a room (for stats)
     */
    public int getTotalReactionCount(String roomCode) {
        Map<String, List<MessageReaction>> messageReactions = roomReactions.get(roomCode);
        if (messageReactions == null) {
            return 0;
        }
        return messageReactions.values().stream()
                .mapToInt(List::size)
                .sum();
    }
}
