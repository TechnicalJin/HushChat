package com.code.HushChat.service;

import com.code.HushChat.dto.WebSocketEventDto;
import com.code.HushChat.dto.ReactionResponseDto;
import com.code.HushChat.dto.ReactionResponseDto.EmojiCount;
import com.code.HushChat.dto.ReactionResponseDto.ReactionSummary;
import com.code.HushChat.exception.RoomNotFoundException;
import com.code.HushChat.exception.UnauthorizedException;
import com.code.HushChat.model.ChatRoom;
import com.code.HushChat.model.MessageReaction;
import com.code.HushChat.model.event.BaseRealtimeEvent;
import com.code.HushChat.model.event.RealtimeEvent;
import com.code.HushChat.realtime.RealtimeDispatcher;
import com.code.HushChat.storage.InMemoryReactionStore;
import com.code.HushChat.storage.InMemoryRoomStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

// NOTE: Long polling (PollEventDto) intentionally removed.
// All real-time communication now uses WebSocket-only transport.

/**
 * Service for managing message reactions (emoji reactions).
 * Uses RealtimeDispatcher for WebSocket event delivery.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReactionService {
    
    private final InMemoryReactionStore reactionStore;
    private final InMemoryRoomStore roomStore;
    private final RealtimeDispatcher realtimeDispatcher;
    
    // Default quick reaction emojis (matching Instagram/WhatsApp)
    public static final List<String> DEFAULT_REACTIONS = List.of("❤️", "😂", "😮", "😢", "😡", "👍");
    
    /**
     * Toggle a reaction on a message.
     * If the user already has this reaction, remove it. Otherwise, add it.
     * 
     * @return ReactionResponseDto with action "added" or "removed"
     */
    public ReactionResponseDto toggleReaction(String roomCode, String messageId, String userId, String emoji) {
        // Validate room exists
        ChatRoom room = roomStore.get(roomCode);
        if (room == null || room.isExpired()) {
            throw new RoomNotFoundException("Room not found or expired: " + roomCode);
        }
        
        // Validate user is in room
        if (!room.getActiveUserIds().contains(userId)) {
            throw new UnauthorizedException("User is not a member of this room");
        }
        
        // Get user name
        String userName = room.getUserIdToName().getOrDefault(userId, "Unknown");
        
        // Create reaction object
        MessageReaction reaction = MessageReaction.builder()
                .reactionId(UUID.randomUUID().toString())
                .messageId(messageId)
                .roomCode(roomCode)
                .userId(userId)
                .userName(userName)
                .emoji(emoji)
                .createdAt(LocalDateTime.now())
                .build();
        
        // Toggle the reaction
        MessageReaction result = reactionStore.toggleReaction(reaction);
        
        String action = result != null ? "added" : "removed";
        log.info("Reaction {} {} on message {} by user {}", emoji, action, messageId, userId);
        
        // Dispatch reaction event via WebSocket to all room members
        dispatchReactionEvent(roomCode, messageId, emoji, userId, userName, action);
        
        return ReactionResponseDto.builder()
                .reactionId(result != null ? result.getReactionId() : null)
                .messageId(messageId)
                .roomCode(roomCode)
                .userId(userId)
                .userName(userName)
                .emoji(emoji)
                .createdAt(result != null ? result.getCreatedAt() : null)
                .action(action)
                .build();
    }
    
    /**
     * Dispatch reaction event via WebSocket.
     */
    private void dispatchReactionEvent(String roomCode, String messageId, String emoji, 
                                     String userId, String userName, String action) {
        try {
            // Get updated reaction counts for the message
            Map<String, WebSocketEventDto.ReactionInfo> updatedCounts = getReactionInfoForMessage(roomCode, messageId);
            
            // Create reaction event
            WebSocketEventDto reactionEvent = WebSocketEventDto.reactionEvent(
                    roomCode,
                    messageId,
                    emoji,
                    userId,
                    userName,
                    action,
                    updatedCounts
            );
            
            // Wrap in RealtimeEvent and dispatch
            RealtimeEvent event = BaseRealtimeEvent.reactionEvent(roomCode, reactionEvent);
            realtimeDispatcher.dispatch(event, null);
            
            log.debug("Dispatched reaction event for room {} via WebSocket: {} by {}", 
                     roomCode, emoji, userId);
        } catch (Exception e) {
            log.error("Failed to dispatch reaction event: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Get reaction info in WebSocketEventDto format for a message
     */
    private Map<String, WebSocketEventDto.ReactionInfo> getReactionInfoForMessage(String roomCode, String messageId) {
        List<MessageReaction> reactions = reactionStore.getReactionsForMessage(roomCode, messageId);
        
        Map<String, WebSocketEventDto.ReactionInfo> result = new HashMap<>();
        
        // Group reactions by emoji
        Map<String, List<MessageReaction>> grouped = reactions.stream()
                .collect(Collectors.groupingBy(MessageReaction::getEmoji));
        
        for (Map.Entry<String, List<MessageReaction>> entry : grouped.entrySet()) {
            String emojiKey = entry.getKey();
            List<MessageReaction> emojiReactions = entry.getValue();
            
            result.put(emojiKey, WebSocketEventDto.ReactionInfo.builder()
                    .count(emojiReactions.size())
                    .userIds(emojiReactions.stream().map(MessageReaction::getUserId).collect(Collectors.toList()))
                    .userNames(emojiReactions.stream().map(MessageReaction::getUserName).collect(Collectors.toList()))
                    .build());
        }
        
        return result;
    }
    
    /**
     * Add a reaction to a message
     */
    public ReactionResponseDto addReaction(String roomCode, String messageId, String userId, String emoji) {
        // Validate room exists
        ChatRoom room = roomStore.get(roomCode);
        if (room == null || room.isExpired()) {
            throw new RoomNotFoundException("Room not found or expired: " + roomCode);
        }
        
        // Validate user is in room
        if (!room.getActiveUserIds().contains(userId)) {
            throw new UnauthorizedException("User is not a member of this room");
        }
        
        // Get user name
        String userName = room.getUserIdToName().getOrDefault(userId, "Unknown");
        
        // Create reaction
        MessageReaction reaction = MessageReaction.builder()
                .reactionId(UUID.randomUUID().toString())
                .messageId(messageId)
                .roomCode(roomCode)
                .userId(userId)
                .userName(userName)
                .emoji(emoji)
                .createdAt(LocalDateTime.now())
                .build();
        
        boolean added = reactionStore.addReaction(reaction);
        
        if (!added) {
            log.debug("Reaction already exists: {} on {} by {}", emoji, messageId, userId);
        }
        
        return ReactionResponseDto.builder()
                .reactionId(reaction.getReactionId())
                .messageId(messageId)
                .roomCode(roomCode)
                .userId(userId)
                .userName(userName)
                .emoji(emoji)
                .createdAt(reaction.getCreatedAt())
                .action(added ? "added" : "exists")
                .build();
    }
    
    /**
     * Remove a reaction from a message
     */
    public ReactionResponseDto removeReaction(String roomCode, String messageId, String userId, String emoji) {
        // Validate room exists
        ChatRoom room = roomStore.get(roomCode);
        if (room == null || room.isExpired()) {
            throw new RoomNotFoundException("Room not found or expired: " + roomCode);
        }
        
        boolean removed = reactionStore.removeReaction(roomCode, messageId, userId, emoji);
        
        return ReactionResponseDto.builder()
                .messageId(messageId)
                .roomCode(roomCode)
                .userId(userId)
                .emoji(emoji)
                .action(removed ? "removed" : "not_found")
                .build();
    }
    
    /**
     * Get reaction summary for a message
     */
    public ReactionSummary getReactionSummary(String roomCode, String messageId) {
        List<MessageReaction> reactions = reactionStore.getReactionsForMessage(roomCode, messageId);
        
        Map<String, EmojiCount> reactionMap = new HashMap<>();
        
        // Group reactions by emoji
        Map<String, List<MessageReaction>> grouped = reactions.stream()
                .collect(Collectors.groupingBy(MessageReaction::getEmoji));
        
        for (Map.Entry<String, List<MessageReaction>> entry : grouped.entrySet()) {
            String emoji = entry.getKey();
            List<MessageReaction> emojiReactions = entry.getValue();
            
            reactionMap.put(emoji, EmojiCount.builder()
                    .emoji(emoji)
                    .count(emojiReactions.size())
                    .userIds(emojiReactions.stream().map(MessageReaction::getUserId).collect(Collectors.toList()))
                    .userNames(emojiReactions.stream().map(MessageReaction::getUserName).collect(Collectors.toList()))
                    .build());
        }
        
        return ReactionSummary.builder()
                .messageId(messageId)
                .reactions(reactionMap)
                .build();
    }
    
    /**
     * Get reaction summaries for multiple messages
     */
    public Map<String, ReactionSummary> getReactionSummaries(String roomCode, List<String> messageIds) {
        Map<String, ReactionSummary> result = new HashMap<>();
        
        Map<String, List<MessageReaction>> allReactions = reactionStore.getReactionsForMessages(roomCode, messageIds);
        
        for (String messageId : messageIds) {
            List<MessageReaction> reactions = allReactions.getOrDefault(messageId, Collections.emptyList());
            
            if (!reactions.isEmpty()) {
                Map<String, EmojiCount> reactionMap = new HashMap<>();
                
                Map<String, List<MessageReaction>> grouped = reactions.stream()
                        .collect(Collectors.groupingBy(MessageReaction::getEmoji));
                
                for (Map.Entry<String, List<MessageReaction>> entry : grouped.entrySet()) {
                    String emoji = entry.getKey();
                    List<MessageReaction> emojiReactions = entry.getValue();
                    
                    reactionMap.put(emoji, EmojiCount.builder()
                            .emoji(emoji)
                            .count(emojiReactions.size())
                            .userIds(emojiReactions.stream().map(MessageReaction::getUserId).collect(Collectors.toList()))
                            .userNames(emojiReactions.stream().map(MessageReaction::getUserName).collect(Collectors.toList()))
                            .build());
                }
                
                result.put(messageId, ReactionSummary.builder()
                        .messageId(messageId)
                        .reactions(reactionMap)
                        .build());
            }
        }
        
        return result;
    }
    
    /**
     * Check if user has reacted with specific emoji
     */
    public boolean hasUserReacted(String roomCode, String messageId, String userId, String emoji) {
        return reactionStore.hasUserReacted(roomCode, messageId, userId, emoji);
    }
    
    /**
     * Remove all reactions for a message (called when message is deleted)
     */
    public void removeAllReactionsForMessage(String roomCode, String messageId) {
        reactionStore.removeAllReactionsForMessage(roomCode, messageId);
    }
    
    /**
     * Remove all reactions for a room (called when room is deleted)
     */
    public void removeAllReactionsForRoom(String roomCode) {
        reactionStore.removeAllReactionsForRoom(roomCode);
    }
}
