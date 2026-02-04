package com.code.HushChat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Redis statistics and health information.
 * 
 * @author Hush Chat Development Team
 * @since 3.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedisStats {
    
    /**
     * Total number of keys in Redis database
     */
    private long totalKeys;
    
    /**
     * Memory used by Redis (formatted string, e.g., "1.5M")
     */
    private String memoryUsed;
    
    /**
     * Number of currently connected clients
     */
    private int connectedClients;
    
    /**
     * Redis server uptime in seconds
     */
    private long uptimeInSeconds;
    
    /**
     * Redis version
     */
    private String version;
    
    /**
     * Redis mode (standalone, sentinel, cluster)
     */
    private String mode;
    
    /**
     * Docker container status
     */
    private boolean dockerContainerRunning;
}
