package com.code.HushChat.service;

import com.code.HushChat.dto.RedisStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Service for monitoring Redis health and statistics.
 * Useful for development and troubleshooting Docker Redis connection.
 * 
 * <p>Docker Redis Commands:
 * <pre>
 * Start Redis:
 *   docker-compose up -d redis
 * 
 * Stop Redis:
 *   docker-compose stop redis
 * 
 * View Logs:
 *   docker-compose logs -f redis
 * 
 * Connect to CLI:
 *   docker exec -it chat-redis redis-cli
 * 
 * Check Keys:
 *   docker exec -it chat-redis redis-cli KEYS "room:code:*"
 * 
 * Monitor Commands:
 *   docker exec -it chat-redis redis-cli MONITOR
 * 
 * Clear All Data:
 *   docker exec -it chat-redis redis-cli FLUSHDB
 * </pre>
 * </p>
 * 
 * @author Hush Chat Development Team
 * @since 3.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisHealthService {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Check Redis connection health.
     * 
     * @return true if Redis is connected and responding, false otherwise
     */
    public boolean checkConnection() {
        try {
            String pong = redisTemplate.getConnectionFactory().getConnection().ping();
            boolean isConnected = "PONG".equalsIgnoreCase(pong);
            
            if (isConnected) {
                log.info("Redis health check: HEALTHY (Docker container: chat-redis)");
            } else {
                log.warn("Redis health check: UNHEALTHY - unexpected response: {}", pong);
            }
            
            return isConnected;
        } catch (Exception e) {
            log.error("Redis health check: FAILED - Docker container may not be running", e);
            log.error("Start Redis: docker-compose up -d redis");
            return false;
        }
    }

    /**
     * Get Redis server information.
     * 
     * @return map of Redis server info (version, mode, clients, etc.)
     */
    public Map<String, String> getInfo() {
        Map<String, String> info = new HashMap<>();
        
        try {
            Properties properties = redisTemplate.getConnectionFactory()
                .getConnection()
                .serverCommands()
                .info();
            
            // Extract relevant information
            info.put("redis_version", properties.getProperty("redis_version", "unknown"));
            info.put("redis_mode", properties.getProperty("redis_mode", "standalone"));
            info.put("connected_clients", properties.getProperty("connected_clients", "0"));
            info.put("used_memory_human", properties.getProperty("used_memory_human", "0"));
            info.put("uptime_in_seconds", properties.getProperty("uptime_in_seconds", "0"));
            info.put("docker_container", "chat-redis");
            info.put("docker_image", "redis:7.2-alpine");
            
            log.info("Retrieved Redis server info: version={}, mode={}, clients={}", 
                info.get("redis_version"), info.get("redis_mode"), info.get("connected_clients"));
            
        } catch (Exception e) {
            log.error("Failed to retrieve Redis info. Is Docker container running?", e);
            info.put("error", e.getMessage());
            info.put("troubleshoot", "Run: docker-compose up -d redis");
        }
        
        return info;
    }

    /**
     * Get current Redis statistics.
     * 
     * @return RedisStats object with current statistics
     */
    public RedisStats getStats() {
        try {
            RedisConnection connection = redisTemplate.getConnectionFactory().getConnection();
            Properties info = connection.serverCommands().info();
            
            // Count total keys (using DBSIZE command)
            Long dbSize = connection.serverCommands().dbSize();
            
            RedisStats stats = RedisStats.builder()
                .totalKeys(dbSize != null ? dbSize : 0)
                .memoryUsed(info.getProperty("used_memory_human", "0"))
                .connectedClients(Integer.parseInt(info.getProperty("connected_clients", "0")))
                .uptimeInSeconds(Long.parseLong(info.getProperty("uptime_in_seconds", "0")))
                .version(info.getProperty("redis_version", "unknown"))
                .mode(info.getProperty("redis_mode", "standalone"))
                .dockerContainerRunning(true)
                .build();
            
            log.info("Redis stats - Keys: {}, Memory: {}, Clients: {}, Uptime: {}s",
                stats.getTotalKeys(), stats.getMemoryUsed(), 
                stats.getConnectedClients(), stats.getUptimeInSeconds());
            
            return stats;
            
        } catch (Exception e) {
            log.error("Failed to retrieve Redis stats", e);
            return RedisStats.builder()
                .totalKeys(0)
                .memoryUsed("N/A")
                .connectedClients(0)
                .uptimeInSeconds(0)
                .version("unknown")
                .mode("unknown")
                .dockerContainerRunning(false)
                .build();
        }
    }

    /**
     * Clear all room codes from Redis.
     * Useful for testing and cleanup.
     * 
     * @return number of room codes deleted
     */
    public int clearRoomCodes() {
        try {
            Set<String> keys = redisTemplate.keys("room:code:*");
            
            if (keys == null || keys.isEmpty()) {
                log.info("No room codes found to clear");
                return 0;
            }
            
            Long deletedCount = redisTemplate.delete(keys);
            int deleted = deletedCount != null ? deletedCount.intValue() : 0;
            
            log.info("Cleared {} room codes from Redis", deleted);
            return deleted;
            
        } catch (Exception e) {
            log.error("Failed to clear room codes", e);
            return 0;
        }
    }

    /**
     * Get count of active room codes.
     * 
     * @return number of room codes in Redis
     */
    public long getRoomCodeCount() {
        try {
            Set<String> keys = redisTemplate.keys("room:code:*");
            long count = keys != null ? keys.size() : 0;
            log.debug("Active room codes: {}", count);
            return count;
        } catch (Exception e) {
            log.error("Failed to count room codes", e);
            return 0;
        }
    }
}
