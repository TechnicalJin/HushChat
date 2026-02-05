package com.code.HushChat.service;

import com.code.HushChat.config.AppConfig;
import com.code.HushChat.dto.MessageResponseDto;
import com.code.HushChat.dto.PollEventDto;
import com.code.HushChat.dto.SendMessageDto;
import com.code.HushChat.exception.RoomNotFoundException;
import com.code.HushChat.exception.UnauthorizedException;
import com.code.HushChat.model.ChatMessage;
import com.code.HushChat.model.ChatRoom;
import com.code.HushChat.model.event.BaseRealtimeEvent;
import com.code.HushChat.model.event.RealtimeEvent;
import com.code.HushChat.realtime.RealtimeDispatcher;
import com.code.HushChat.storage.InMemoryMessageStore;
import com.code.HushChat.storage.InMemoryRoomStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {
    
    private final InMemoryMessageStore messageStore;
    private final InMemoryRoomStore roomStore;
    private final RoomService roomService;
    private final AppConfig appConfig;
    
    // LongPollManager used ONLY for poll registration (pollEvents method)
    // Event NOTIFICATION uses RealtimeDispatcher (transport-agnostic)
    private final LongPollManager longPollManager;
    private final RealtimeDispatcher realtimeDispatcher;
    
    // Maximum preview text length for replies
    private static final int MAX_PREVIEW_LENGTH = 100;
    
    /**
     * Send a message to a room (with optional reply support)
     */
    public MessageResponseDto sendMessage(String roomCode, String userId, String content) {
        return sendMessage(roomCode, userId, content, null);
    }
    
    /**
     * Send a message to a room with optional reply metadata
     */
    public MessageResponseDto sendMessage(String roomCode, String userId, String content, 
                                           SendMessageDto.ReplyToRequest replyToRequest) {
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
        
        // Build reply metadata if provided
        ChatMessage.ReplyTo replyTo = null;
        if (replyToRequest != null && replyToRequest.getMessageId() != null) {
            // Validate the replied message exists (optional, for robustness)
            ChatMessage repliedMessage = messageStore.getMessage(roomCode, replyToRequest.getMessageId());
            
            // Build reply metadata with truncated preview
            String previewText = replyToRequest.getPreviewText();
            if (previewText != null && previewText.length() > MAX_PREVIEW_LENGTH) {
                previewText = previewText.substring(0, MAX_PREVIEW_LENGTH) + "...";
            }
            
            replyTo = ChatMessage.ReplyTo.builder()
                    .messageId(replyToRequest.getMessageId())
                    .senderId(replyToRequest.getSenderId())
                    .senderName(replyToRequest.getSenderName())
                    .messageType(replyToRequest.getMessageType())
                    .previewText(previewText)
                    .deleted(repliedMessage != null && repliedMessage.isDeleted())
                    .build();
        }
        
        ChatMessage message = ChatMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .roomCode(roomCode)
                .userId(userId)
                .content(content)
                .type(ChatMessage.MessageType.TEXT)
                .timestamp(now)
                .expiryTime(expiryTime)
                .replyTo(replyTo)
                .build();
        
        // Save message
        messageStore.save(roomCode, message);
        
        // Update room activity
        roomService.updateRoomActivity(roomCode);
        
        log.debug("Message sent in room {} by user {}{}", roomCode, userId, 
                  replyTo != null ? " (reply to " + replyTo.getMessageId() + ")" : "");
        
        // Create response DTO
        MessageResponseDto responseDto = toMessageResponseDto(message, senderName);
        
        // Notify all pending long-poll requests for this room about the new message
        notifyPendingPollsNewMessage(roomCode, responseDto);
        
        return responseDto;
    }
    
    /**
     * Notify all pending long-poll requests about a new message.
     */
    private void notifyPendingPollsNewMessage(String roomCode, MessageResponseDto message) {
        try {
            RealtimeEvent event = BaseRealtimeEvent.messageEvent(roomCode, 
                PollEventDto.messageEvent(roomCode, message));
            realtimeDispatcher.dispatch(event, null);
            log.debug("Notified pending polls for room {} about new message", roomCode);
        } catch (Exception e) {
            log.error("Failed to notify pending polls for new message: {}", e.getMessage(), e);
        }
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
     * Long polling - wait for new messages OR reaction events.
     * Uses event-driven approach via LongPollManager for immediate notification.
     * 
     * @return List of poll events (messages and reactions)
     */
    public List<PollEventDto> pollEvents(String roomCode, LocalDateTime sinceTimestamp, 
                                          String userId, int timeoutSeconds) throws InterruptedException {
        // Validate room exists and user is a member
        ChatRoom room = roomStore.get(roomCode);
        if (room == null || room.isExpired()) {
            throw new RoomNotFoundException("Room not found or expired: " + roomCode);
        }
        if (!room.getActiveUserIds().contains(userId)) {
            throw new UnauthorizedException("User is not a member of this room");
        }
        
        // First, check if there are already new messages available
        List<MessageResponseDto> existingMessages = getMessagesSince(roomCode, sinceTimestamp, userId);
        if (!existingMessages.isEmpty()) {
            // Convert to poll events and return immediately
            return existingMessages.stream()
                    .map(msg -> PollEventDto.messageEvent(roomCode, msg))
                    .collect(Collectors.toList());
        }
        
        // No existing messages, register for long-poll notification
        LongPollManager.PendingPoll pendingPoll = longPollManager.registerPoll(roomCode, userId, timeoutSeconds);
        
        try {
            // Wait for events or timeout
            CompletableFuture<List<PollEventDto>> future = pendingPoll.getFuture();
            List<PollEventDto> events = future.get(timeoutSeconds, TimeUnit.SECONDS);
            
            return events != null ? events : Collections.emptyList();
        } catch (java.util.concurrent.TimeoutException e) {
            // Timeout - return empty list
            return Collections.emptyList();
        } catch (java.util.concurrent.ExecutionException e) {
            log.error("Poll execution error: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * Legacy long polling - wait for new messages only (for backward compatibility)
     * @deprecated Use pollEvents() instead for unified message + reaction updates
     */
    @Deprecated
    public List<MessageResponseDto> pollMessages(String roomCode, LocalDateTime sinceTimestamp, 
                                                  String userId, int timeoutSeconds) throws InterruptedException {
        // Use the new event-based polling and extract only messages
        List<PollEventDto> events = pollEvents(roomCode, sinceTimestamp, userId, timeoutSeconds);
        
        return events.stream()
                .filter(e -> e.getType() == PollEventDto.EventType.MESSAGE || 
                            e.getType() == PollEventDto.EventType.MESSAGE_EDIT ||
                            e.getType() == PollEventDto.EventType.MESSAGE_DELETE)
                .map(PollEventDto::getMessage)
                .filter(msg -> msg != null)
                .collect(Collectors.toList());
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
        // Convert reply metadata if present
        MessageResponseDto.ReplyToDto replyToDto = null;
        if (message.getReplyTo() != null) {
            ChatMessage.ReplyTo replyTo = message.getReplyTo();
            
            // Check if original message still exists and update deleted status
            ChatMessage originalMessage = messageStore.getMessage(message.getRoomCode(), replyTo.getMessageId());
            boolean isDeleted = originalMessage == null || originalMessage.isDeleted();
            
            replyToDto = MessageResponseDto.ReplyToDto.builder()
                    .messageId(replyTo.getMessageId())
                    .senderId(replyTo.getSenderId())
                    .senderName(replyTo.getSenderName())
                    .messageType(replyTo.getMessageType())
                    .previewText(isDeleted ? null : replyTo.getPreviewText())
                    .deleted(isDeleted)
                    .build();
        }
        
        return MessageResponseDto.builder()
                .messageId(message.getMessageId())
                .roomCode(message.getRoomCode())
                .senderId(message.getUserId())
                .senderName(senderName)
                .content(message.getContent())
                .type(message.getType().name())
                .fileId(message.getFileId())
                .timestamp(message.getTimestamp())
                .expiryTime(message.getExpiryTime())
                .edited(message.isEdited())
                .deleted(message.isDeleted())
                .lastModified(message.getLastModified())
                .replyTo(replyToDto)
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
        
        MessageResponseDto responseDto = toMessageResponseDto(updatedMessage, senderName != null ? senderName : "Unknown");
        
        // Notify all pending polls about the edit
        notifyPendingPollsMessageEdit(roomCode, responseDto);
        
        return responseDto;
    }
    
    /**
     * Notify all pending long-poll requests about a message edit.
     */
    private void notifyPendingPollsMessageEdit(String roomCode, MessageResponseDto message) {
        try {
            RealtimeEvent event = BaseRealtimeEvent.customEvent(
                com.code.HushChat.model.event.EventType.MESSAGE_EDIT,
                roomCode,
                PollEventDto.messageEditEvent(roomCode, message));
            realtimeDispatcher.dispatch(event, null);
            log.debug("Notified pending polls for room {} about message edit", roomCode);
        } catch (Exception e) {
            log.error("Failed to notify pending polls for message edit: {}", e.getMessage(), e);
        }
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
        
        MessageResponseDto responseDto = toMessageResponseDto(deletedMessage, senderName != null ? senderName : "Unknown");
        
        // Notify all pending polls about the deletion
        notifyPendingPollsMessageDelete(roomCode, responseDto);
        
        return responseDto;
    }
    
    /**
     * Notify all pending long-poll requests about a message deletion.
     */
    private void notifyPendingPollsMessageDelete(String roomCode, MessageResponseDto message) {
        try {
            RealtimeEvent event = BaseRealtimeEvent.customEvent(
                com.code.HushChat.model.event.EventType.MESSAGE_DELETE,
                roomCode,
                PollEventDto.messageDeleteEvent(roomCode, message));
            realtimeDispatcher.dispatch(event, null);
            log.debug("Notified pending polls for room {} about message delete", roomCode);
        } catch (Exception e) {
            log.error("Failed to notify pending polls for message delete: {}", e.getMessage(), e);
        }
    }
}
