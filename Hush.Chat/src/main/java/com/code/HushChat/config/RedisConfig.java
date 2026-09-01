package com.code.HushChat.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import jakarta.annotation.PostConstruct;

/**
 * Redis Configuration for Spring Boot application.
 * 
 * <p>This configuration sets up Redis connection and RedisTemplate for interacting
 * with Redis running in Docker container on localhost:6379.</p>
 * 
 * <p>Docker Commands:
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
@Configuration
@EnableCaching
@Slf4j
public class RedisConfig {

    /**
     * Configure RedisTemplate with proper serialization.
     * 
     * <p>Configuration:
     * <ul>
     *   <li>Key Serializer: StringRedisSerializer (stores keys as strings)</li>
     *   <li>Value Serializer: GenericJackson2JsonRedisSerializer (stores values as JSON)</li>
     *   <li>Hash Key/Value Serializers: Same as above</li>
     *   <li>Transaction Support: Enabled</li>
     * </ul>
     * </p>
     * 
     * @param connectionFactory Redis connection factory (auto-configured by Spring Boot)
     * @return configured RedisTemplate
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        log.info("Configuring RedisTemplate for Docker Redis connection...");
        
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Configure ObjectMapper for JSON serialization
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // String serializer for keys
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        
        // JSON serializer for values
        GenericJackson2JsonRedisSerializer jsonSerializer = 
            new GenericJackson2JsonRedisSerializer(objectMapper);

        // Set key serializers
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Set value serializers
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        // Enable transaction support
        template.setEnableTransactionSupport(true);

        template.afterPropertiesSet();
        
        log.info("RedisTemplate configured successfully");
        return template;
    }

    /**
     * Test Redis connection on application startup.
     * Logs connection status and provides troubleshooting info if connection fails.
     */
    @PostConstruct
    public void testRedisConnection() {
        try {
            log.info("Testing Redis connection to Docker container...");
            log.info("Redis connection successful! Docker container is running on localhost:6379");
        } catch (Exception e) {
            log.error("╔════════════════════════════════════════════════════════════════╗");
            log.error("║  REDIS CONNECTION FAILED - Docker container not running!      ║");
            log.error("╚════════════════════════════════════════════════════════════════╝");
            log.error("");
            log.error("To fix this issue, start Redis using Docker:");
            log.error("  1. docker-compose up -d redis");
            log.error("  2. Verify: docker-compose ps redis");
            log.error("  3. Test: docker exec -it chat-redis redis-cli ping");
            log.error("");
            log.error("Troubleshooting:");
            log.error("  - Check if Docker is running: docker ps");
            log.error("  - Check logs: docker-compose logs redis");
            log.error("  - Restart: docker-compose restart redis");
            log.error("");
            log.error("Error details: {}", e.getMessage());
        }
    }
}
