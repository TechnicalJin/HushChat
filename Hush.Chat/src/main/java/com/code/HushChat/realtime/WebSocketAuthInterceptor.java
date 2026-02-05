package com.code.HushChat.realtime;

import com.code.HushChat.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket handshake interceptor for JWT authentication.
 * Validates JWT tokens before allowing WebSocket connection.
 * 
 * Authentication flow:
 * 1. Client connects: ws://host/ws?token={jwt}
 * 2. Interceptor extracts token from query parameter
 * 3. Validates token using JwtTokenProvider
 * 4. Extracts userId from token claims
 * 5. Stores userId in WebSocket session attributes
 * 6. Allows/denies connection based on validation
 * 
 * Security:
 * - Rejects connections without valid JWT
 * - Rejects expired tokens
 * - Logs all authentication attempts
 * - Prevents unauthorized WebSocket access
 * 
 * @since 1.0.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthInterceptor implements HandshakeInterceptor {
    
    private final JwtTokenProvider jwtTokenProvider;
    
    /**
     * Query parameter name for JWT token.
     */
    private static final String TOKEN_PARAM = "token";
    
    /**
     * Session attribute key for storing userId.
     */
    private static final String USER_ID_ATTRIBUTE = "userId";
    
    /**
     * Before WebSocket handshake: Extract and validate JWT token.
     * 
     * @param request WebSocket upgrade request
     * @param response WebSocket upgrade response
     * @param wsHandler WebSocket handler
     * @param attributes Session attributes to populate
     * @return true to allow connection, false to reject
     */
    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        
        try {
            // Extract token from query parameters
            String token = extractTokenFromRequest(request);
            
            if (token == null || token.isEmpty()) {
                log.warn("WebSocket connection rejected: No token provided in query parameters");
                return false;
            }
            
            // Validate token
            if (!jwtTokenProvider.validateToken(token)) {
                log.warn("WebSocket connection rejected: Invalid or expired JWT token");
                return false;
            }
            
            // Extract userId from token claims
            String userId = jwtTokenProvider.getUserIdFromToken(token);
            
            if (userId == null || userId.isEmpty()) {
                log.warn("WebSocket connection rejected: Token does not contain userId");
                return false;
            }
            
            // Store userId in session attributes for later use
            attributes.put(USER_ID_ATTRIBUTE, userId);
            
            // Log successful authentication
            String remoteAddress = getRemoteAddress(request);
            log.info("WebSocket authentication successful: userId={}, remoteAddress={}", 
                     userId, remoteAddress);
            
            return true;
            
        } catch (Exception e) {
            log.error("WebSocket authentication failed with exception: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * After WebSocket handshake: Log successful connection.
     * 
     * @param request WebSocket upgrade request
     * @param response WebSocket upgrade response
     * @param wsHandler WebSocket handler
     * @param exception Exception if handshake failed (null if successful)
     */
    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        
        if (exception != null) {
            log.error("WebSocket handshake failed: {}", exception.getMessage());
        } else {
            String remoteAddress = getRemoteAddress(request);
            log.debug("WebSocket handshake completed successfully from {}", remoteAddress);
        }
    }
    
    /**
     * Extract JWT token from query parameters.
     * 
     * Expected format: ws://host/ws?token={jwt}
     * 
     * @param request WebSocket upgrade request
     * @return JWT token, or null if not found
     */
    private String extractTokenFromRequest(ServerHttpRequest request) {
        String query = request.getURI().getQuery();
        
        if (query == null || query.isEmpty()) {
            return null;
        }
        
        // Parse query string for token parameter
        String[] params = query.split("&");
        for (String param : params) {
            String[] keyValue = param.split("=", 2);
            if (keyValue.length == 2 && TOKEN_PARAM.equals(keyValue[0])) {
                return keyValue[1];
            }
        }
        
        return null;
    }
    
    /**
     * Get remote IP address from request.
     * 
     * @param request WebSocket upgrade request
     * @return Remote IP address, or "unknown" if not available
     */
    private String getRemoteAddress(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            return servletRequest.getServletRequest().getRemoteAddr();
        }
        return "unknown";
    }
}