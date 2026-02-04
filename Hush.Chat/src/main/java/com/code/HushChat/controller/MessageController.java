package com.code.HushChat.controller;

import com.code.HushChat.dto.MessageResponseDto;
import com.code.HushChat.dto.PollEventDto;
import com.code.HushChat.dto.SendMessageDto;
import com.code.HushChat.model.ApiResponse;
import com.code.HushChat.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
@Slf4j
public class MessageController {
    
    private final MessageService messageService;
    
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    
    /**
     * Send a message to a room (with optional reply support)
     * POST /api/messages/send
     */
    @PostMapping("/send")
    public ResponseEntity<ApiResponse<MessageResponseDto>> sendMessage(
            @Valid @RequestBody SendMessageDto dto) {
        
        String roomCode = dto.getRoomCode().toUpperCase();
        String userId = dto.getUserId();
        
        log.info("Sending message to room: {} by user: {}{}", roomCode, userId,
                 dto.getReplyTo() != null ? " (reply)" : "");
        
        MessageResponseDto message = messageService.sendMessage(
                roomCode, 
                userId, 
                dto.getContent(),
                dto.getReplyTo()
        );
        
        return ResponseEntity.ok(
                ApiResponse.success("Message sent", message)
        );
    }
    
    /**
     * Get messages since a given timestamp
     * GET /api/messages/{roomCode}?since={timestamp}&userId={userId}
     */
    @GetMapping("/{roomCode}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMessages(
            @PathVariable String roomCode,
            @RequestParam String userId,
            @RequestParam(required = false) String since) {
        
        roomCode = roomCode.toUpperCase();
        
        // Parse since timestamp
        LocalDateTime sinceTimestamp = null;
        if (since != null && !since.isBlank()) {
            try {
                sinceTimestamp = LocalDateTime.parse(since, FORMATTER);
            } catch (DateTimeParseException e) {
                log.warn("Invalid timestamp format: {}", since);
            }
        }
        
        List<MessageResponseDto> messages = messageService.getMessagesSince(
                roomCode, 
                sinceTimestamp, 
                userId
        );
        
        Map<String, Object> response = new HashMap<>();
        response.put("messages", messages);
        response.put("count", messages.size());
        response.put("serverTime", LocalDateTime.now().format(FORMATTER));
        
        return ResponseEntity.ok(
                ApiResponse.success("Messages retrieved", response)
        );
    }
    
    /**
     * Long polling endpoint - waits for new messages OR reaction events.
     * Returns unified events that include both messages and reaction updates.
     * GET /api/messages/{roomCode}/poll?since={timestamp}&userId={userId}&timeout={seconds}
     * 
     * Response includes:
     * - events: List of PollEventDto (MESSAGE, MESSAGE_EDIT, MESSAGE_DELETE, REACTION)
     * - messages: List of MessageResponseDto (backward compatibility - extracted from events)
     * - count: Total event count
     * - serverTime: Current server time
     */
    @GetMapping("/{roomCode}/poll")
    public ResponseEntity<ApiResponse<Map<String, Object>>> pollMessages(
            @PathVariable String roomCode,
            @RequestParam String userId,
            @RequestParam(required = false) String since,
            @RequestParam(defaultValue = "20") int timeout) {
        
        roomCode = roomCode.toUpperCase();
        
        // Limit timeout to 25 seconds max
        timeout = Math.min(timeout, 25);
        
        // Parse since timestamp
        LocalDateTime sinceTimestamp = null;
        if (since != null && !since.isBlank()) {
            try {
                sinceTimestamp = LocalDateTime.parse(since, FORMATTER);
            } catch (DateTimeParseException e) {
                log.warn("Invalid timestamp format: {}", since);
            }
        }
        
        try {
            // Use the new event-based polling
            List<PollEventDto> events = messageService.pollEvents(
                    roomCode, 
                    sinceTimestamp, 
                    userId,
                    timeout
            );
            
            // Extract messages from events for backward compatibility
            List<MessageResponseDto> messages = events.stream()
                    .filter(e -> e.getType() == PollEventDto.EventType.MESSAGE || 
                                e.getType() == PollEventDto.EventType.MESSAGE_EDIT ||
                                e.getType() == PollEventDto.EventType.MESSAGE_DELETE)
                    .map(PollEventDto::getMessage)
                    .filter(msg -> msg != null)
                    .toList();
            
            Map<String, Object> response = new HashMap<>();
            response.put("events", events);     // Full event list with reactions
            response.put("messages", messages); // Backward compatibility
            response.put("count", events.size());
            response.put("serverTime", LocalDateTime.now().format(FORMATTER));
            
            return ResponseEntity.ok(
                    ApiResponse.success("Poll completed", response)
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Poll interrupted for room: {}", roomCode);
            
            Map<String, Object> response = new HashMap<>();
            response.put("events", List.of());
            response.put("messages", List.of());
            response.put("count", 0);
            response.put("serverTime", LocalDateTime.now().format(FORMATTER));
            
            return ResponseEntity.ok(
                    ApiResponse.success("Poll interrupted", response)
            );
        }
    }
    
    /**
     * Edit a message
     * PUT /api/messages/{roomCode}/{messageId}
     */
    @PutMapping("/{roomCode}/{messageId}")
    public ResponseEntity<ApiResponse<MessageResponseDto>> editMessage(
            @PathVariable String roomCode,
            @PathVariable String messageId,
            @RequestBody Map<String, String> body) {
        
        roomCode = roomCode.toUpperCase();
        String userId = body.get("userId");
        String content = body.get("content");
        
        if (userId == null || content == null) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("userId and content are required")
            );
        }
        
        log.info("Editing message {} in room {} by user {}", messageId, roomCode, userId);
        
        MessageResponseDto message = messageService.editMessage(roomCode, messageId, userId, content);
        
        return ResponseEntity.ok(
                ApiResponse.success("Message edited", message)
        );
    }
    
    /**
     * Unsend (soft delete) a message
     * DELETE /api/messages/{roomCode}/{messageId}
     */
    @DeleteMapping("/{roomCode}/{messageId}")
    public ResponseEntity<ApiResponse<MessageResponseDto>> unsendMessage(
            @PathVariable String roomCode,
            @PathVariable String messageId,
            @RequestParam String userId) {
        
        roomCode = roomCode.toUpperCase();
        
        log.info("Unsending message {} in room {} by user {}", messageId, roomCode, userId);
        
        MessageResponseDto message = messageService.unsendMessage(roomCode, messageId, userId);
        
        return ResponseEntity.ok(
                ApiResponse.success("Message unsent", message)
        );
    }
    
    /**
     * Get message count for a room (for debugging/monitoring)
     * GET /api/messages/{roomCode}/count
     */
    @GetMapping("/{roomCode}/count")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> getMessageCount(
            @PathVariable String roomCode) {
        
        roomCode = roomCode.toUpperCase();
        int count = messageService.getMessageCount(roomCode);
        
        Map<String, Integer> response = new HashMap<>();
        response.put("count", count);
        
        return ResponseEntity.ok(
                ApiResponse.success("Message count retrieved", response)
        );
    }
}
