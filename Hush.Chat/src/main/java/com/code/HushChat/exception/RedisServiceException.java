package com.code.HushChat.exception;

/**
 * Custom exception for Redis service failures.
 * Provides helpful troubleshooting information for Docker Redis connection issues.
 * 
 * @author Hush Chat Development Team
 * @since 3.0
 */
public class RedisServiceException extends RuntimeException {

    /**
     * Constructor with default Docker troubleshooting message.
     * 
     * @param message the error message
     */
    public RedisServiceException(String message) {
        super(message + "\n\n" +
              "╔════════════════════════════════════════════════════════════╗\n" +
              "║  Redis Connection Failed - Troubleshooting Steps          ║\n" +
              "╚════════════════════════════════════════════════════════════╝\n" +
              "\n" +
              "1. Ensure Docker is running:\n" +
              "   docker --version\n" +
              "\n" +
              "2. Start Redis container:\n" +
              "   docker-compose up -d redis\n" +
              "\n" +
              "3. Verify Redis is running:\n" +
              "   docker-compose ps redis\n" +
              "\n" +
              "4. Test Redis connection:\n" +
              "   docker exec -it chat-redis redis-cli ping\n" +
              "   (Should return: PONG)\n" +
              "\n" +
              "5. Check Redis logs:\n" +
              "   docker-compose logs -f redis\n" +
              "\n" +
              "6. Restart Redis if needed:\n" +
              "   docker-compose restart redis\n");
    }

    /**
     * Constructor with custom message and cause.
     * 
     * @param message the error message
     * @param cause the underlying cause
     */
    public RedisServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
