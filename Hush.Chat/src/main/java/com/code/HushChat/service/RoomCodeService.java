package com.code.HushChat.service;

import com.code.HushChat.exception.RedisServiceException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing room codes using Docker Redis.
 * 
 * <p>Room codes are temporary, unique identifiers for chat rooms stored in Redis
 * with TTL (Time To Live). Codes automatically expire after the configured duration.</p>
 * 
 * <p>Redis Key Pattern:
 * <pre>
 * room:code:{CODE} → stores creatorUserId
 * Example: room:code:ABC123 → "550e8400-e29b-41d4-a716-446655440000"
 * </pre>
 * </p>
 * 
 * <p>Docker Redis Commands for Testing:
 * <pre>
 * # Connect to Redis CLI
 * docker exec -it chat-redis redis-cli
 * 
 * # Set test room code (expires in 3600 seconds)
 * SET "room:code:TEST123" "user-id" EX 3600
 * 
 * # Get room code
 * GET "room:code:TEST123"
 * 
 * # Check TTL (time to live)
 * TTL "room:code:TEST123"
 * 
 * # List all room codes
 * KEYS "room:code:*"
 * 
 * # Delete room code
 * DEL "room:code:TEST123"
 * 
 * # Delete all room codes
 * redis-cli --scan --pattern "room:code:*" | xargs redis-cli DEL
 * </pre>
 * </p>
 * 
 * @author Hush Chat Development Team
 * @since 3.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RoomCodeService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.room.code.ttl:3600}")
    private long roomCodeTtl;

    @Value("${app.room.code.length:6}")
    private int roomCodeLength;

    @Value("${app.room.code.max-retries:5}")
    private int maxRetries;

    private static final String REDIS_KEY_PREFIX = "room:code:";
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Verify Redis connection on service initialization.
     * Logs connection status and configuration.
     */
    @PostConstruct
    public void init() {
        if (verifyRedisConnection()) {
            log.info("RoomCodeService initialized successfully");
            log.info("Redis configuration - TTL: {}s, Code Length: {}, Max Retries: {}", 
                roomCodeTtl, roomCodeLength, maxRetries);
        } else {
            log.warn("RoomCodeService initialized but Redis connection is unavailable");
        }
    }

    /**
     * Generate a unique room code.
     * 
     * <p>Generates a random alphanumeric code and ensures uniqueness by checking Redis.
     * Format: ABC123 (6 uppercase alphanumeric characters)</p>
     * 
     * @return unique room code
     * @throws RedisServiceException if max retries exceeded or Redis is unavailable
     */
    public String generateRoomCode() {
        try {
            for (int attempt = 0; attempt < maxRetries; attempt++) {
                String code = generateRandomCode();
                
                // Check if code already exists in Redis
                String redisKey = REDIS_KEY_PREFIX + code;
                Boolean exists = redisTemplate.hasKey(redisKey);
                
                if (Boolean.FALSE.equals(exists)) {
                    log.info("Generated unique room code: {} (attempt: {})", code, attempt + 1);
                    return code;
                }
                
                log.debug("Room code {} already exists, retrying... (attempt: {})", code, attempt + 1);
            }
            
            log.warn("Room code generation failed after {} retries", maxRetries);
            throw new RedisServiceException("Failed to generate unique room code after " + maxRetries + " attempts");
            
        } catch (RedisConnectionFailureException e) {
            log.error("Redis connection failed while generating room code", e);
            throw new RedisServiceException("Failed to generate room code - Redis connection unavailable", e);
        }
    }

    /**
     * Store a room code in Redis with TTL.
     * 
     * @param roomCode the room code to store
     * @param creatorUserId the UUID of the user who created the room
     * @param ttl the time-to-live duration (or null for default)
     * @throws RedisServiceException if Redis operation fails
     */
    public void storeRoomCode(String roomCode, UUID creatorUserId, Duration ttl) {
        try {
            String redisKey = REDIS_KEY_PREFIX + roomCode;
            Duration actualTtl = ttl != null ? ttl : Duration.ofSeconds(roomCodeTtl);
            
            redisTemplate.opsForValue().set(redisKey, creatorUserId.toString(), actualTtl);
            
            log.info("Stored room code: {} for user: {} with TTL: {} seconds", 
                roomCode, creatorUserId, actualTtl.getSeconds());
            
        } catch (RedisConnectionFailureException e) {
            log.error("Redis connection failed while storing room code: {}", roomCode, e);
            throw new RedisServiceException("Failed to store room code - Redis connection unavailable", e);
        }
    }

    /**
     * Retrieve the creator user ID for a room code.
     * 
     * @param roomCode the room code to look up
     * @return Optional containing the creator's UUID if found, empty if expired or not found
     * @throws RedisServiceException if Redis operation fails
     */
    public Optional<UUID> getRoomCodeCreator(String roomCode) {
        try {
            String redisKey = REDIS_KEY_PREFIX + roomCode;
            Object value = redisTemplate.opsForValue().get(redisKey);
            
            if (value == null) {
                log.debug("Room code not found or expired: {}", roomCode);
                return Optional.empty();
            }
            
            UUID creatorId = UUID.fromString(value.toString());
            log.info("Retrieved creator: {} for room code: {}", creatorId, roomCode);
            return Optional.of(creatorId);
            
        } catch (RedisConnectionFailureException e) {
            log.error("Redis connection failed while retrieving room code: {}", roomCode, e);
            throw new RedisServiceException("Failed to retrieve room code - Redis connection unavailable", e);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format in Redis for room code: {}", roomCode, e);
            return Optional.empty();
        }
    }

    /**
     * Delete a room code from Redis.
     * Called after successful room join to prevent code reuse.
     * 
     * @param roomCode the room code to delete
     * @throws RedisServiceException if Redis operation fails
     */
    public void deleteRoomCode(String roomCode) {
        try {
            String redisKey = REDIS_KEY_PREFIX + roomCode;
            Boolean deleted = redisTemplate.delete(redisKey);
            
            if (Boolean.TRUE.equals(deleted)) {
                log.info("Deleted room code: {} after successful join", roomCode);
            } else {
                log.warn("Attempted to delete non-existent room code: {}", roomCode);
            }
            
        } catch (RedisConnectionFailureException e) {
            log.error("Redis connection failed while deleting room code: {}", roomCode, e);
            throw new RedisServiceException("Failed to delete room code - Redis connection unavailable", e);
        }
    }

    /**
     * Verify Redis connection.
     * 
     * @return true if Redis is connected, false otherwise
     */
    public boolean verifyRedisConnection() {
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            log.debug("Redis connection verified successfully (Docker container: chat-redis)");
            return true;
        } catch (Exception e) {
            log.warn("Redis connection failed: {} - Run: docker-compose up -d redis", e.getMessage());
            return false;
        }
    }

    /**
     * Generate a random alphanumeric code.
     * 
     * @return random code string (uppercase)
     */
    private String generateRandomCode() {
        StringBuilder code = new StringBuilder(roomCodeLength);
        for (int i = 0; i < roomCodeLength; i++) {
            code.append(CHARACTERS.charAt(RANDOM.nextInt(CHARACTERS.length())));
        }
        return code.toString();
    }
}
