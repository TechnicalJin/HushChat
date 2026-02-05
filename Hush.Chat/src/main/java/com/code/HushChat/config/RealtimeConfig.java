package com.code.HushChat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for real-time communication mode.
 * Supports gradual migration from Long Polling to WebSocket.
 * 
 * Usage:
 * - LONG_POLL: Traditional HTTP long polling (default, current implementation)
 * - WEBSOCKET: WebSocket-based real-time communication (future implementation)
 * 
 * @since 1.0.0
 */
@Configuration
@ConfigurationProperties(prefix = "realtime")
@Data
public class RealtimeConfig {
    
    /**
     * Real-time communication mode.
     * Default: LONG_POLL (maintains backward compatibility)
     */
    private RealtimeMode mode = RealtimeMode.LONG_POLL;
    
    /**
     * Real-time communication modes
     */
    public enum RealtimeMode {
        /**
         * HTTP Long Polling - Current implementation
         * Client repeatedly polls /poll endpoint, server holds connection up to timeout
         */
        LONG_POLL,
        
        /**
         * WebSocket - Future implementation
         * Bi-directional persistent connection for real-time events
         */
        WEBSOCKET
    }
    
    /**
     * Check if long polling mode is active
     */
    public boolean isLongPollMode() {
        return mode == RealtimeMode.LONG_POLL;
    }
    
    /**
     * Check if WebSocket mode is active
     */
    public boolean isWebSocketMode() {
        return mode == RealtimeMode.WEBSOCKET;
    }
}
