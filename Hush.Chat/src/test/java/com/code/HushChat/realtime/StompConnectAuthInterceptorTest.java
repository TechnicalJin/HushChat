package com.code.HushChat.realtime;

import com.code.HushChat.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StompConnectAuthInterceptor}.
 *
 * Verifies STOMP CONNECT authentication:
 * - valid Bearer JWT produces an authenticated Principal (name == userId)
 * - missing/malformed Authorization headers are rejected
 * - invalid/expired JWTs are rejected
 * - a JWT without an extractable userId is rejected
 *
 * @since 3.0.0
 */
@ExtendWith(MockitoExtension.class)
class StompConnectAuthInterceptorTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private MessageChannel messageChannel;

    private StompConnectAuthInterceptor interceptor;

    private static final String TEST_USER_ID = "user-123";

    @BeforeEach
    void setUp() {
        interceptor = new StompConnectAuthInterceptor(jwtTokenProvider);
    }

    // ---------------------------------------------------------------
    // Success cases
    // ---------------------------------------------------------------

    @Test
    void connectWithValidBearerJwtSetsAuthenticatedPrincipal() {
        when(jwtTokenProvider.validateToken("valid.jwt.token")).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken("valid.jwt.token")).thenReturn(TEST_USER_ID);

        Message<?> connectMessage = createConnectMessage("Bearer valid.jwt.token");

        Message<?> result = interceptor.preSend(connectMessage, messageChannel);

        assertThat(result).isNotNull();
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(result);
        assertThat(accessor.getUser()).isNotNull();
        assertThat(accessor.getUser().getName()).isEqualTo(TEST_USER_ID);
    }

    @Test
    void connectWithValidBearerJwtPrincipalNameEqualsExpectedUserId() {
        when(jwtTokenProvider.validateToken("abcdef")).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken("abcdef")).thenReturn(TEST_USER_ID);

        Message<?> connectMessage = createConnectMessage("Bearer abcdef");

        Message<?> result = interceptor.preSend(connectMessage, messageChannel);

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(result);
        assertThat(accessor.getUser().getName()).isEqualTo(TEST_USER_ID);
    }

    @Test
    void connectWithLowercaseBearerSchemeIsAccepted() {
        when(jwtTokenProvider.validateToken("tokenLower")).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken("tokenLower")).thenReturn(TEST_USER_ID);

        Message<?> connectMessage = createConnectMessage("bearer tokenLower");

        Message<?> result = interceptor.preSend(connectMessage, messageChannel);

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(result);
        assertThat(accessor.getUser().getName()).isEqualTo(TEST_USER_ID);
    }


    // ---------------------------------------------------------------
    // Rejection cases
    // ---------------------------------------------------------------

    @Test
    void connectWithMissingAuthorizationIsRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-1");
        accessor.setLeaveMutable(true);
        Message<?> connectMessage = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(connectMessage, messageChannel))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing Authorization");
    }

    @Test
    void connectWithEmptyAuthorizationIsRejected() {
        Message<?> connectMessage = createConnectMessage("  ");

        assertThatThrownBy(() -> interceptor.preSend(connectMessage, messageChannel))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing Authorization");
    }

    @Test
    void connectWithNonBearerSchemeIsRejectedAsMalformed() {
        Message<?> connectMessage = createConnectMessage("Basic abc");

        assertThatThrownBy(() -> interceptor.preSend(connectMessage, messageChannel))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("malformed");
    }

    @Test
    void connectWithEmptyTokenIsRejectedAsMalformed() {
        Message<?> connectMessage = createConnectMessage("Bearer   ");

        assertThatThrownBy(() -> interceptor.preSend(connectMessage, messageChannel))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("malformed");
    }

    @Test
    void connectWithInvalidJwtIsRejected() {
        when(jwtTokenProvider.validateToken("bad.jwt")).thenReturn(false);

        Message<?> connectMessage = createConnectMessage("Bearer bad.jwt");

        assertThatThrownBy(() -> interceptor.preSend(connectMessage, messageChannel))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid or expired JWT");
    }

    @Test
    void connectWithExpiredJwtIsRejected() {
        when(jwtTokenProvider.validateToken("expired.jwt")).thenReturn(false);

        Message<?> connectMessage = createConnectMessage("Bearer expired.jwt");

        assertThatThrownBy(() -> interceptor.preSend(connectMessage, messageChannel))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid or expired JWT");
    }

    @Test
    void connectWithJwtWithoutUserIdIsRejected() {
        when(jwtTokenProvider.validateToken("no.user.token")).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken("no.user.token")).thenReturn(null);

        Message<?> connectMessage = createConnectMessage("Bearer no.user.token");

        assertThatThrownBy(() -> interceptor.preSend(connectMessage, messageChannel))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId");
    }

    @Test
    void connectWithJwtUserIdExtractionFailureIsRejected() {
        when(jwtTokenProvider.validateToken("boom")).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken("boom"))
                .thenThrow(new RuntimeException("parsing failed"));

        Message<?> connectMessage = createConnectMessage("Bearer boom");

        assertThatThrownBy(() -> interceptor.preSend(connectMessage, messageChannel))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unable to extract userId");
    }


    // ---------------------------------------------------------------
    // Non-CONNECT commands pass through untouched
    // ---------------------------------------------------------------

    @Test
    void nonConnectCommandsAreNotAuthenticatedOrRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId("session-1");
        accessor.setLeaveMutable(true);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, messageChannel);

        assertThat(result).isNotNull();
        assertThat(StompHeaderAccessor.wrap(result).getCommand()).isEqualTo(StompCommand.SUBSCRIBE);
        assertThat(StompHeaderAccessor.wrap(result).getUser()).isNull();
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private Message<?> createConnectMessage(String authorizationValue) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-1");
        // Leave the accessor mutable so the interceptor can set the UserPrincipal,
        // mirroring how Spring's STOMP inbound flow presents CONNECT frames.
        accessor.setLeaveMutable(true);
        accessor.setNativeHeader("Authorization", authorizationValue);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}

