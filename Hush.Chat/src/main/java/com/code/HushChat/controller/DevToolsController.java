package com.code.HushChat.controller;

import com.code.HushChat.dto.RedisStats;
import com.code.HushChat.model.ApiResponse;
import com.code.HushChat.service.RedisHealthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Development tools controller for Redis monitoring.
 * 
 * <p>⚠️ Only enabled in development profile for security!</p>
 * 
 * <p>Endpoints:
 * <ul>
 *   <li>GET /api/dev/redis/status - Redis health and statistics</li>
 *   <li>GET /api/dev/redis/info - Detailed Redis server info</li>
 *   <li>POST /api/dev/redis/clear-room-codes - Clear all room codes</li>
 *   <li>GET /api/dev/redis/room-code-count - Count active room codes</li>
 * </ul>
 * </p>
 * 
 * @author Hush Chat Development Team
 * @since 3.0
 */
@RestController
@RequestMapping("/api/dev/redis")
@RequiredArgsConstructor
@Slf4j
@Profile("!prod") // Not available in production
public class DevToolsController {

    private final RedisHealthService redisHealthService;

    /**
     * Get Redis status and statistics.
     * 
     * @return Redis stats including connection status, memory usage, etc.
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<RedisStats>> getRedisStatus() {
        log.info("Development endpoint called: GET /api/dev/redis/status");
        
        RedisStats stats = redisHealthService.getStats();
        
        return ResponseEntity.ok(ApiResponse.<RedisStats>builder()
            .success(stats.isDockerContainerRunning())
            .message(stats.isDockerContainerRunning() 
                ? "Redis is running in Docker container" 
                : "Redis connection failed - ensure Docker container is running")
            .data(stats)
            .build());
    }

    /**
     * Get detailed Redis server information.
     * 
     * @return map of Redis server properties
     */
    @GetMapping("/info")
    public ResponseEntity<ApiResponse<Map<String, String>>> getRedisInfo() {
        log.info("Development endpoint called: GET /api/dev/redis/info");
        
        Map<String, String> info = redisHealthService.getInfo();
        boolean hasError = info.containsKey("error");
        
        return ResponseEntity.ok(ApiResponse.<Map<String, String>>builder()
            .success(!hasError)
            .message(hasError ? "Failed to retrieve Redis info" : "Redis info retrieved successfully")
            .data(info)
            .build());
    }

    /**
     * Check Redis connection health.
     * 
     * @return connection status
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkHealth() {
        log.info("Development endpoint called: GET /api/dev/redis/health");
        
        boolean isHealthy = redisHealthService.checkConnection();
        
        Map<String, Object> healthData = new HashMap<>();
        healthData.put("healthy", isHealthy);
        healthData.put("dockerContainer", "chat-redis");
        healthData.put("port", "6379");
        
        if (!isHealthy) {
            healthData.put("troubleshoot", "docker-compose up -d redis");
        }
        
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
            .success(isHealthy)
            .message(isHealthy ? "Redis is healthy" : "Redis connection failed")
            .data(healthData)
            .build());
    }

    /**
     * Clear all room codes from Redis.
     * Useful for testing.
     * 
     * @return number of room codes deleted
     */
    @PostMapping("/clear-room-codes")
    public ResponseEntity<ApiResponse<Map<String, Object>>> clearRoomCodes() {
        log.warn("Development endpoint called: POST /api/dev/redis/clear-room-codes - Clearing all room codes!");
        
        int deletedCount = redisHealthService.clearRoomCodes();
        
        Map<String, Object> result = new HashMap<>();
        result.put("deletedCount", deletedCount);
        result.put("operation", "clear_room_codes");
        
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
            .success(true)
            .message("Cleared " + deletedCount + " room codes from Redis")
            .data(result)
            .build());
    }

    /**
     * Get count of active room codes.
     * 
     * @return number of room codes in Redis
     */
    @GetMapping("/room-code-count")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getRoomCodeCount() {
        log.info("Development endpoint called: GET /api/dev/redis/room-code-count");
        
        long count = redisHealthService.getRoomCodeCount();
        
        Map<String, Object> result = new HashMap<>();
        result.put("count", count);
        result.put("pattern", "room:code:*");
        
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
            .success(true)
            .message("Active room codes: " + count)
            .data(result)
            .build());
    }
}
