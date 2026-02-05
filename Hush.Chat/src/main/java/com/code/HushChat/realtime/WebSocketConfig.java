package com.code.HushChat.realtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

/**
 * WebSocket configuration for STOMP messaging over WebSocket.
 * 
 * Architecture:
 * - Endpoint: /ws (with SockJS fallback)
 * - Message broker: /topic (broadcast), /queue (point-to-point)
 * - Application prefix: /app (client -> server messages)
 * - User prefix: /user (targeted user messages)
 * 
 * Message flow examples:
 * 1. Client sends message:
 *    - Client: SEND /app/message/ABC123 {content}
 *    - Server: Broadcasts to /topic/room/ABC123
 * 
 * 2. Client receives room events:
 *    - Client: SUBSCRIBE /topic/room/ABC123
 *    - Server: Sends events to all subscribers
 * 
 * 3. Targeted user message:
 *    - Server: Sends to /user/{userId}/queue/notification
 *    - Client: SUBSCRIBE /user/queue/notification
 * 
 * Security:
 * - JWT authentication via WebSocketAuthInterceptor
 * - CORS configured for allowed origins
 * - Message size limits enforced
 * 
 * @since 1.0.0
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    
    private final WebSocketAuthInterceptor authInterceptor;
    private final CustomHandshakeHandler handshakeHandler;
    
    /**
     * WebSocket endpoint path.
     */
    private static final String WEBSOCKET_ENDPOINT = "/ws";
    
    /**
     * Allowed origins for CORS.
     * TODO: Update for production with actual domain
     */
    private static final String[] ALLOWED_ORIGINS = {
        "http://localhost:8080",
        "http://127.0.0.1:8080",
        // Add production URLs here
    };
    
    /**
     * Register STOMP endpoints for WebSocket communication.
     * 
     * Endpoint configuration:
     * - Base endpoint: /ws
     * - Authentication: JWT token in query parameter
     * - CORS: Configured for allowed origins
     * - SockJS fallback: Enabled for older browsers
     * 
     * Client connection example:
     * - WebSocket: ws://host/ws?token={jwt}
     * - SockJS: http://host/ws?token={jwt}
     * 
     * @param registry STOMP endpoint registry
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        log.info("Registering WebSocket STOMP endpoint: {}", WEBSOCKET_ENDPOINT);
        
        registry.addEndpoint(WEBSOCKET_ENDPOINT)
                // Configure CORS - only allow explicitly configured origins
                .setAllowedOrigins(ALLOWED_ORIGINS)
                // Add JWT authentication interceptor
                .addInterceptors(authInterceptor)
                // CRITICAL FIX: Set custom handshake handler to create StompPrincipal
                // This enables convertAndSendToUser() to route messages correctly
                .setHandshakeHandler(handshakeHandler)
                // Enable SockJS fallback for browsers without WebSocket support
                .withSockJS()
                    // Optional: Configure SockJS options
                    .setHeartbeatTime(25000) // 25 seconds
                    .setDisconnectDelay(5000); // 5 seconds
        
        log.info("WebSocket endpoint registered with SockJS fallback, JWT authentication, and custom handshake handler");
    }
    
    /**
     * Configure message broker for routing STOMP messages.
     * 
     * Broker configuration:
     * - /topic: Broadcast messages (one-to-many)
     * - /queue: Point-to-point messages (one-to-one)
     * - /app: Application destination prefix (client -> server)
     * - /user: User-specific destination prefix
     * 
     * Destination patterns:
     * - /topic/room/{roomCode}: Broadcast to all users in room
     * - /user/{userId}/queue/notification: Private message to user
     * - /app/message/{roomCode}: Client sends message to server
     * 
     * @param registry Message broker registry
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        log.info("Configuring STOMP message broker");
        
        // Create TaskScheduler for heartbeats
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(1);
        taskScheduler.setThreadNamePrefix("ws-heartbeat-");
        taskScheduler.initialize();
        
        // Enable simple in-memory broker for /topic and /queue destinations
        registry.enableSimpleBroker("/topic", "/queue")
                .setTaskScheduler(taskScheduler)
                .setHeartbeatValue(new long[]{10000, 10000}); // 10 seconds
        
        // Set application destination prefix for client -> server messages
        registry.setApplicationDestinationPrefixes("/app");
        
        // Set user destination prefix for targeted user messages
        registry.setUserDestinationPrefix("/user");
        
        log.info("Message broker configured: /topic, /queue, /app, /user");
    }
    
    /**
     * Configure WebSocket transport options.
     * 
     * Transport settings:
     * - Message size limits
     * - Send buffer size
     * - Send time limits
     * 
     * @param registry WebSocket transport registry
     */
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        log.info("Configuring WebSocket transport settings");
        
        registry
            // Maximum message size: 128 KB (should handle most messages)
            .setMessageSizeLimit(128 * 1024)
            
            // Send buffer size: 512 KB
            .setSendBufferSizeLimit(512 * 1024)
            
            // Send timeout: 20 seconds
            .setSendTimeLimit(20 * 1000);
        
        log.info("WebSocket transport configured: messageSize=128KB, bufferSize=512KB, timeout=20s");
    }
}