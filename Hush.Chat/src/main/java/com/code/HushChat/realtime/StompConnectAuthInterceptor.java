package com.code.HushChat.realtime;

import com.code.HushChat.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * STOMP CONNECT authentication interceptor.
 *
 * Authenticates WebSocket/STOMP sessions by validating the JWT presented in
 * the STOMP CONNECT frame's {@code Authorization} header.
 *
 * Authentication flow:
 * 1. Client establishes WebSocket handshake to /ws (no JWT in the URL)
 * 2. Client sends STOMP CONNECT with header: Authorization: Bearer {jwt}
 * 3. This interceptor intercepts the inbound CONNECT frame
 * 4. Extracts the JWT and validates it via the EXISTING JwtTokenProvider
 * 5. On success, sets a StompPrincipal(userId) on the session so that
 *    convertAndSendToUser() and Principal-annotated handlers keep working
 * 6. On failure, an ERROR frame is sent back and the CONNECT is rejected
 *
 * Security:
 * - Rejects CONNECT when the Authorization header is missing/malformed
 * - Rejects invalid or expired JWTs
 * - Rejects tokens from which a userId cannot be extracted
 * - Never logs the JWT value itself
 *
 * @since 3.0.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StompConnectAuthInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Authorization header name in the STOMP CONNECT frame.
     */
    private static final String AUTHORIZATION_HEADER = "Authorization";

    /**
     * Authorization scheme prefix (case-insensitive).
     */
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * Session attribute key for storing the authenticated userId.
     */
    static final String USER_ID_ATTRIBUTE = "userId";

    /**
     * Intercept inbound messages and authenticate the STOMP CONNECT command.
     *
     * @param message   Inbound message (CONNECT frame on connection establishment)
     * @param channel   Message channel
     * @return The (possibly modified) message; rejection is signalled via an
     *         exception, causing an ERROR frame to be returned to the client
     */
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                message, StompHeaderAccessor.class);

        // Only authenticate the CONNECT command (performed once per session)
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticate(accessor, message);
        }

        return message;
    }

    /**
     * Validate the JWT from the CONNECT frame's Authorization header and,
     * on success, set the StompPrincipal on the STOMP session.
     *
     * On any failure, an {@link IllegalArgumentException} is thrown which
     * Spring converts into a STOMP ERROR frame, gracefully rejecting the
     * unauthenticated CONNECT.
     *
     * @param accessor STOMP header accessor for the CONNECT frame
     * @param message  The inbound message
     */
    private void authenticate(StompHeaderAccessor accessor, Message<?> message) {
        String authorization = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);

        if (authorization == null || authorization.trim().isEmpty()) {
            log.warn("[STOMP CONNECT] Rejected: missing Authorization header");
            throw new IllegalArgumentException(
                    "Authentication required: missing Authorization header on STOMP CONNECT");
        }

        String token = extractBearerToken(authorization);

        if (token == null) {
            log.warn("[STOMP CONNECT] Rejected: malformed Authorization header");
            throw new IllegalArgumentException(
                    "Authentication failed: malformed Authorization header");
        }

        // Validate using the EXISTING JwtTokenProvider (no new JWT implementation)
        if (!jwtTokenProvider.validateToken(token)) {
            log.warn("[STOMP CONNECT] Rejected: invalid or expired JWT token");
            throw new IllegalArgumentException(
                    "Authentication failed: invalid or expired JWT token");
        }


        String userId;
        try {
            userId = jwtTokenProvider.getUserIdFromToken(token);
        } catch (Exception e) {
            log.warn("[STOMP CONNECT] Rejected: unable to extract userId from JWT: {}",
                    e.getMessage());
            throw new IllegalArgumentException(
                    "Authentication failed: unable to extract userId from JWT");
        }

        if (userId == null || userId.isEmpty()) {
            log.warn("[STOMP CONNECT] Rejected: JWT does not contain a userId");
            throw new IllegalArgumentException(
                    "Authentication failed: JWT does not contain a userId");
        }

        // Establish the Principal for this STOMP session so that
        // convertAndSendToUser() and Principal-annotated @MessageMapping handlers work.
        StompPrincipal principal = new StompPrincipal(userId);
        accessor.setUser(principal);

        // Also store the userId in session attributes for other handlers
        if (accessor.getSessionAttributes() != null) {
            accessor.getSessionAttributes().put(USER_ID_ATTRIBUTE, userId);
        }

        log.info("[STOMP CONNECT] Authenticated: userId={}, sessionId={}",
                userId, accessor.getSessionId());
    }

    /**
     * Extract a Bearer token value from an Authorization header value.
     *
     * Expected format: "Bearer {jwt}"
     *
     * @param authorization Header value
     * @return The token, or null if the header is malformed / not Bearer scheme
     */
    private String extractBearerToken(String authorization) {
        String trimmed = authorization.trim();
        if (trimmed.length() <= BEARER_PREFIX.length()
                || !trimmed.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        String token = trimmed.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}

