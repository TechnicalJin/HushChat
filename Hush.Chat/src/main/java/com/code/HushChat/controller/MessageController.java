package com.code.HushChat.controller;

import com.code.HushChat.dto.MessageResponseDto;
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
     * Send a message to a room
     * POST /api/messages/send
     */
    @PostMapping("/send")
    public ResponseEntity<ApiResponse<MessageResponseDto>> sendMessage(
            @Valid @RequestBody SendMessageDto dto) {
        
        String roomCode = dto.getRoomCode().toUpperCase();
        String userId = dto.getUserId();
        
        log.info("Sending message to room: {} by user: {}", roomCode, userId);
        
        MessageResponseDto message = messageService.sendMessage(
                roomCode, 
                userId, 
                dto.getContent()
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
     * Long polling endpoint - waits for new messages
     * GET /api/messages/{roomCode}/poll?since={timestamp}&userId={userId}&timeout={seconds}
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
            List<MessageResponseDto> messages = messageService.pollMessages(
                    roomCode, 
                    sinceTimestamp, 
                    userId,
                    timeout
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("messages", messages);
            response.put("count", messages.size());
            response.put("serverTime", LocalDateTime.now().format(FORMATTER));
            
            return ResponseEntity.ok(
                    ApiResponse.success("Poll completed", response)
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Poll interrupted for room: {}", roomCode);
            
            Map<String, Object> response = new HashMap<>();
            response.put("messages", List.of());
            response.put("count", 0);
            response.put("serverTime", LocalDateTime.now().format(FORMATTER));
            
            return ResponseEntity.ok(
                    ApiResponse.success("Poll interrupted", response)
            );
        }
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
