package com.code.HushChat.realtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import java.util.Arrays;
import java.util.List;

/**
 * WebSocket configuration for STOMP messaging over WebSocket.
 *
 * Architecture:
 * - Endpoint: /ws (with SockJS fallback)
 * - Message broker: /topic (broadcast), /queue (point-to-point)
 * - Application prefix: /app (client -> server messages)
 * - User prefix: /user (targeted user messages)
 *
 * Authentication:
 * - JWT authentication is performed at the STOMP CONNECT stage via
 *   {@link StompConnectAuthInterceptor} (Authorization header), NOT via
 *   a query parameter on the handshake URL.
 * - CORS origins are property-driven via {@code cors.allowed-origins}.
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
 * @since 1.0.0
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompConnectAuthInterceptor stompConnectAuthInterceptor;

    /**
     * WebSocket endpoint path.
     */
    private static final String WEBSOCKET_ENDPOINT = "/ws";

    /**
     * Comma-separated list of allowed CORS origins, injected from the
     * {@code cors.allowed-origins} application property.
     */
    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    /**
     * Register STOMP endpoints for WebSocket communication.
     *
     * Endpoint configuration:
     * - Base endpoint: /ws
     * - Authentication: JWT via STOMP CONNECT Authorization header
     * - CORS: Property-driven (cors.allowed-origins) using origin patterns
     * - SockJS fallback: Enabled for older browsers
     *
     * @param registry STOMP endpoint registry
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        log.info("Registering WebSocket STOMP endpoint: {}", WEBSOCKET_ENDPOINT);

        List<String> origins = parseAllowedOrigins();

        registry.addEndpoint(WEBSOCKET_ENDPOINT)
                // Property-driven CORS using origin patterns (supports credentials + wildcards safely)
                .setAllowedOriginPatterns(origins.toArray(new String[0]))
                // Enable SockJS fallback for browsers without WebSocket support
                .withSockJS()
                    // SockJS options
                    .setHeartbeatTime(25000) // 25 seconds
                    .setDisconnectDelay(5000); // 5 seconds

        log.info("WebSocket endpoint registered with SockJS fallback and property-driven CORS origins: {}", origins);
    }

    /**
     * Register the STOMP CONNECT authentication interceptor on the client
     * inbound channel. This is where the JWT from the STOMP CONNECT
     * Authorization header is validated and the Principal is established.
     *
     * @param registration Client inbound channel registration
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        log.info("Registering STOMP CONNECT authentication interceptor");
        registration.interceptors(stompConnectAuthInterceptor);
    }

    /**
     * Configure message broker for routing STOMP messages.
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

    /**
     * Parse the comma-separated {@code cors.allowed-origins} property into a
     * list of origin patterns.
     *
     * The property is required; the list is trimmed of surrounding whitespace
     * and empty entries are dropped.
     *
     * @return List of allowed origin patterns
     */
    private List<String> parseAllowedOrigins() {
        if (allowedOrigins == null || allowedOrigins.trim().isEmpty()) {
            throw new IllegalStateException(
                    "cors.allowed-origins property is not configured. " +
                    "Explicit allowed origins are required; wildcards are not permitted.");
        }

        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }
}

