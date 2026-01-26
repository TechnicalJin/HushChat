package com.code.HushChat.service;

import com.code.HushChat.config.AppConfig;
import com.code.HushChat.dto.MessageResponseDto;
import com.code.HushChat.exception.RoomNotFoundException;
import com.code.HushChat.exception.UnauthorizedException;
import com.code.HushChat.model.ChatMessage;
import com.code.HushChat.model.ChatRoom;
import com.code.HushChat.storage.InMemoryMessageStore;
import com.code.HushChat.storage.InMemoryRoomStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {
    
    private final InMemoryMessageStore messageStore;
    private final InMemoryRoomStore roomStore;
    private final RoomService roomService;
    private final AppConfig appConfig;
    
    /**
     * Send a message to a room
     */
    public MessageResponseDto sendMessage(String roomCode, String userId, String content) {
        // Validate room exists
        ChatRoom room = roomStore.get(roomCode);
        if (room == null || room.isExpired()) {
            throw new RoomNotFoundException("Room not found or expired: " + roomCode);
        }
        
        // Validate user is in room
        if (!room.getActiveUserIds().contains(userId)) {
            throw new UnauthorizedException("User is not a member of this room");
        }
        
        // Get sender name
        String senderName = room.getUserIdToName().get(userId);
        if (senderName == null) {
            senderName = "Unknown";
        }
        
        // Create message with TTL
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiryTime = now.plusMinutes(appConfig.getMessage().getTtlMinutes());
        
        ChatMessage message = ChatMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .roomCode(roomCode)
                .userId(userId)
                .content(content)
                .type(ChatMessage.MessageType.TEXT)
                .timestamp(now)
                .expiryTime(expiryTime)
                .build();
        
        // Save message
        messageStore.save(roomCode, message);
        
        // Update room activity
        roomService.updateRoomActivity(roomCode);
        
        log.debug("Message sent in room {} by user {}", roomCode, userId);
        
        return toMessageResponseDto(message, senderName);
    }
    
    /**
     * Get messages since a given timestamp, respecting user join time
     * Late joiners will NOT see messages sent before they joined
     */
    public List<MessageResponseDto> getMessagesSince(String roomCode, LocalDateTime sinceTimestamp, String userId) {
        // Validate room exists
        ChatRoom room = roomStore.get(roomCode);
        if (room == null) {
            throw new RoomNotFoundException("Room not found: " + roomCode);
        }
        
        // Validate user is in room
        if (!room.getActiveUserIds().contains(userId)) {
            throw new UnauthorizedException("User is not a member of this room");
        }
        
        // Get user join time - this is the privacy boundary
        LocalDateTime userJoinTime = roomStore.getUserJoinTime(roomCode, userId);
        if (userJoinTime == null) {
            // User join time not recorded, default to now (no history)
            userJoinTime = LocalDateTime.now();
        }
        
        // Determine the effective start time (max of sinceTimestamp and userJoinTime)
        LocalDateTime effectiveStartTime = sinceTimestamp;
        if (effectiveStartTime == null || effectiveStartTime.isBefore(userJoinTime)) {
            effectiveStartTime = userJoinTime;
        }
        
        final LocalDateTime startTime = effectiveStartTime;
        final LocalDateTime finalUserJoinTime = userJoinTime;
        final LocalDateTime sinceTime = sinceTimestamp;
        
        // Get messages and filter
        List<ChatMessage> messages = messageStore.getMessages(roomCode);
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }
        
        return messages.stream()
                // Filter: only non-expired messages
                .filter(msg -> !msg.isExpired())
                // Filter: only messages sent after user joined
                .filter(msg -> msg.getTimestamp().isAfter(finalUserJoinTime) || msg.getTimestamp().isEqual(finalUserJoinTime))
                // Filter: messages after start time OR messages modified after since time
                .filter(msg -> {
                    boolean isNew = msg.getTimestamp().isAfter(startTime);
                    boolean isModified = sinceTime != null && msg.getLastModified() != null && 
                                         msg.getLastModified().isAfter(sinceTime);
                    return isNew || isModified;
                })
                .map(msg -> {
                    String senderName = room.getUserIdToName().get(msg.getUserId());
                    if (senderName == null) {
                        senderName = "Unknown";
                    }
                    return toMessageResponseDto(msg, senderName);
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Long polling - wait for new messages
     */
    public List<MessageResponseDto> pollMessages(String roomCode, LocalDateTime sinceTimestamp, 
                                                  String userId, int timeoutSeconds) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        long timeoutMs = timeoutSeconds * 1000L;
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            List<MessageResponseDto> messages = getMessagesSince(roomCode, sinceTimestamp, userId);
            
            if (!messages.isEmpty()) {
                return messages;
            }
            
            // Check if room still exists
            ChatRoom room = roomStore.get(roomCode);
            if (room == null || room.isExpired()) {
                throw new RoomNotFoundException("Room has been closed");
            }
            
            // Wait before checking again
            Thread.sleep(500); // Check every 500ms
        }
        
        // Timeout - return empty list
        return Collections.emptyList();
    }
    
    /**
     * Cleanup expired messages - called by scheduled task
     * Uses the store's optimized cleanup method for thread-safe removal.
     * Privacy: Does NOT log message content, only counts.
     */
    public int cleanupExpiredMessages() {
        int totalRemoved = messageStore.removeAllExpiredMessages();
        
        if (totalRemoved > 0) {
            log.info("Cleanup: removed {} expired messages total", totalRemoved);
        }
        
        return totalRemoved;
    }
    
    /**
     * Get message count for a room
     */
    public int getMessageCount(String roomCode) {
        return messageStore.getRoomMessageCount(roomCode);
    }
    
    /**
     * Convert ChatMessage to MessageResponseDto
     */
    private MessageResponseDto toMessageResponseDto(ChatMessage message, String senderName) {
        return MessageResponseDto.builder()
                .messageId(message.getMessageId())
                .roomCode(message.getRoomCode())
                .senderId(message.getUserId())
                .senderName(senderName)
                .content(message.getContent())
                .type(message.getType().name())
                .timestamp(message.getTimestamp())
                .expiryTime(message.getExpiryTime())
                .edited(message.isEdited())
                .deleted(message.isDeleted())
                .lastModified(message.getLastModified())
                .build();
    }
    
    /**
     * Edit a message
     */
    public MessageResponseDto editMessage(String roomCode, String messageId, String userId, String newContent) {
        // Validate room exists
        ChatRoom room = roomStore.get(roomCode);
        if (room == null || room.isExpired()) {
            throw new RoomNotFoundException("Room not found or expired: " + roomCode);
        }
        
        // Validate user is in room
        if (!room.getActiveUserIds().contains(userId)) {
            throw new UnauthorizedException("User is not a member of this room");
        }
        
        // Get the message
        ChatMessage message = messageStore.getMessage(roomCode, messageId);
        if (message == null) {
            throw new RoomNotFoundException("Message not found: " + messageId);
        }
        
        // Validate user owns the message
        if (!message.getUserId().equals(userId)) {
            throw new UnauthorizedException("You can only edit your own messages");
        }
        
        // Update the message
        boolean updated = messageStore.updateMessage(roomCode, messageId, newContent);
        if (!updated) {
            throw new RoomNotFoundException("Failed to update message");
        }
        
        // Get updated message
        ChatMessage updatedMessage = messageStore.getMessage(roomCode, messageId);
        String senderName = room.getUserIdToName().get(userId);
        
        log.debug("Message {} edited in room {} by user {}", messageId, roomCode, userId);
        
        return toMessageResponseDto(updatedMessage, senderName != null ? senderName : "Unknown");
    }
    
    /**
     * Unsend (soft delete) a message
     */
    public MessageResponseDto unsendMessage(String roomCode, String messageId, String userId) {
        // Validate room exists
        ChatRoom room = roomStore.get(roomCode);
        if (room == null || room.isExpired()) {
            throw new RoomNotFoundException("Room not found or expired: " + roomCode);
        }
        
        // Validate user is in room
        if (!room.getActiveUserIds().contains(userId)) {
            throw new UnauthorizedException("User is not a member of this room");
        }
        
        // Get the message
        ChatMessage message = messageStore.getMessage(roomCode, messageId);
        if (message == null) {
            throw new RoomNotFoundException("Message not found: " + messageId);
        }
        
        // Validate user owns the message
        if (!message.getUserId().equals(userId)) {
            throw new UnauthorizedException("You can only unsend your own messages");
        }
        
        // Mark as deleted
        boolean deleted = messageStore.markMessageDeleted(roomCode, messageId);
        if (!deleted) {
            throw new RoomNotFoundException("Failed to unsend message");
        }
        
        // Get updated message
        ChatMessage deletedMessage = messageStore.getMessage(roomCode, messageId);
        String senderName = room.getUserIdToName().get(userId);
        
        log.debug("Message {} unsent in room {} by user {}", messageId, roomCode, userId);
        
        return toMessageResponseDto(deletedMessage, senderName != null ? senderName : "Unknown");
    }
}
