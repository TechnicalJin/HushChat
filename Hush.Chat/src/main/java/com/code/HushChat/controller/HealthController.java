package com.code.HushChat.controller;

import com.code.HushChat.model.ApiResponse;
import com.code.HushChat.storage.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {
    
    private final InMemoryOtpStore otpStore;
    private final InMemorySessionStore sessionStore;
    private final InMemoryRoomStore roomStore;
    private final InMemoryMessageStore messageStore;
    private final InMemoryFileStore fileStore;
    
    @GetMapping
    public ApiResponse<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        
        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now());
        health.put("service", "Hush.Chat");
        health.put("version", "1.0.0");
        
        return ApiResponse.success("Service is healthy", health);
    }
    
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("activeSessions", sessionStore.size());
        stats.put("activeRooms", roomStore.size());
        stats.put("totalMessages", messageStore.getTotalMessageCount());
        stats.put("totalFiles", fileStore.size());
        stats.put("pendingOtps", otpStore.size());
        stats.put("timestamp", LocalDateTime.now());
        
        return ApiResponse.success("Statistics retrieved", stats);
    }
    
    @GetMapping("/ping")
    public Map<String, String> ping() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "pong");
        response.put("timestamp", LocalDateTime.now().toString());
        return response;
    }
}