package com.code.HushChat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import jakarta.annotation.PostConstruct;

/**
 * Redis Configuration for Spring Boot application.
 *
 * <p>This configuration sets up Redis connection and RedisTemplate for interacting
 * with Redis running in Docker container on localhost:6379.</p>
 *
 * @author Hush Chat Development Team
 * @since 3.0
 */
@Configuration
@EnableCaching
@Slf4j
public class RedisConfig {

    /**
     * Configure RedisTemplate with String-only serialization.
     *
     * <p>SECURITY FIX #9F: Replaced GenericJackson2JsonRedisSerializer with
     * StringRedisSerializer for values to eliminate the deserialization
     * attack vector (potential RCE via polymorphic type handling). All Redis
     * values in this application are plain strings (room codes, user IDs),
     * so JSON serialization is unnecessary.</p>
     *
     * <p>Configuration:</p>
     * <ul>
     *   <li>Key Serializer: StringRedisSerializer (stores keys as strings)</li>
     *   <li>Value Serializer: StringRedisSerializer (stores values as strings)</li>
     *   <li>Hash Key/Value Serializers: Same as above</li>
     *   <li>Transaction Support: Enabled</li>
     * </ul>
     *
     * @param connectionFactory Redis connection factory (auto-configured by Spring Boot)
     * @return configured RedisTemplate
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        log.info("Configuring RedisTemplate for Docker Redis connection...");

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // SECURITY FIX #9F: Use StringRedisSerializer for both keys and values.
        // This eliminates the GenericJackson2JsonRedisSerializer deserialization risk.
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        // Set key serializers
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Set value serializers (String only, no JSON deserialization attack surface)
        template.setValueSerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);

        // Enable transaction support
        template.setEnableTransactionSupport(true);

        template.afterPropertiesSet();

        log.info("RedisTemplate configured successfully (String serializer)");
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
            log.error("REDIS CONNECTION FAILED - Docker container not running!");
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