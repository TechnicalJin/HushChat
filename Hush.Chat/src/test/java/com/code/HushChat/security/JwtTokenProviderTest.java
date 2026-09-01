package com.code.HushChat.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit regression tests for the canonical JWT implementation
 * (SECURITY FIX #8).
 *
 * <p>Covers the token-generation and validation contract that unifies the
 * OTP/session tokens and the room tokens behind {@link JwtTokenProvider}:
 * subject == userId, userId claim preserved, deviceId claim preserved for
 * session tokens, issuer on newly generated tokens, and rejection of
 * expired/malformed/tampered/foreign-key tokens.</p>
 */
class JwtTokenProviderTest {

    // 64 hex chars = valid Base64 -> >= 32 bytes for HS256 key derivation.
    private static final String TEST_SECRET =
            "1b387c484213ab0f9e0cd8d42385b3a07494b6eac8fc015a9428df27173df4d9";
    // A distinct signing key to prove tokens from another key are rejected.
    private static final String OTHER_SECRET =
            "9f8e7d6c5b4a39281706f5e4d3c2b1a09f8e7d6c5b4a39281706f5e4d3c2b1a0";
    private static final long EXPIRATION_MS = 3_600_000L;
    private static final String ISSUER = "hush-chat";
    private static final String USER_ID = "user-123";
    private static final String DEVICE_ID = "device-456";

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret", TEST_SECRET);
        ReflectionTestUtils.setField(provider, "jwtExpirationDate", EXPIRATION_MS);
        ReflectionTestUtils.setField(provider, "jwtIssuer", ISSUER);
    }

    /** 1. JwtTokenProvider generates a valid canonical token. */
    @Test
    void generatesValidCanonicalToken() {
        String token = provider.generateToken(USER_ID);
        assertNotNull(token);
        assertFalse(token.isBlank());
        assertTrue(provider.validateToken(token));
    }

    /** 2. Canonical token validates successfully. */
    @Test
    void canonicalTokenValidates() {
        String token = provider.generateToken(USER_ID);
        assertTrue(provider.validateToken(token));
    }

    /** 3. Subject equals userId for both room and session tokens. */
    @Test
    void subjectEqualsUserId() {
        String roomToken = provider.generateToken(USER_ID);
        assertEquals(USER_ID, provider.getUserIdFromToken(roomToken));

        String sessionToken = provider.generateToken(USER_ID, DEVICE_ID, LocalDateTime.now().plusHours(1));
        assertEquals(USER_ID, provider.getUserIdFromToken(sessionToken));
    }

    /** 4. userId claim is correct for both token flavors. */
    @Test
    void userIdClaimIsCorrect() {
        String roomToken = provider.generateToken(USER_ID);
        assertEquals(USER_ID, provider.getUserIdClaim(roomToken));

        String sessionToken = provider.generateToken(USER_ID, DEVICE_ID, LocalDateTime.now().plusHours(1));
        assertEquals(USER_ID, provider.getUserIdClaim(sessionToken));
    }

    /** 5. deviceId claim is preserved for session tokens. */
    @Test
    void deviceIdClaimPreservedForSessionTokens() {
        String sessionToken = provider.generateToken(USER_ID, DEVICE_ID, LocalDateTime.now().plusHours(1));
        assertEquals(DEVICE_ID, provider.getDeviceId(sessionToken));
    }

    /** Room tokens (no deviceId) carry no deviceId claim. */
    @Test
    void roomTokensHaveNoDeviceId() {
        String roomToken = provider.generateToken(USER_ID);
        assertNull(provider.getDeviceId(roomToken));
    }

    /** 6. issuer is correct for newly generated tokens. */
    @Test
    void issuerIsCorrectForNewlyGeneratedTokens() {
        String roomToken = provider.generateToken(USER_ID);
        Claims roomClaims = parseClaims(roomToken);
        assertEquals(ISSUER, roomClaims.getIssuer());

        String sessionToken = provider.generateToken(USER_ID, DEVICE_ID, LocalDateTime.now().plusHours(1));
        Claims sessionClaims = parseClaims(sessionToken);
        assertEquals(ISSUER, sessionClaims.getIssuer());
    }

    /** 7. expired token is rejected. */
    @Test
    void expiredTokenIsRejected() {
        // Generate a session token that is already in the past.
        String expiredToken = provider.generateToken(
                USER_ID, DEVICE_ID, LocalDateTime.now().minusMinutes(5));
        assertFalse(provider.validateToken(expiredToken));
        // getUserIdFromToken on an expired token must throw.
        try {
            provider.getUserIdFromToken(expiredToken);
            org.junit.jupiter.api.Assertions.fail("Expected JwtException for expired token");
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            // expected
        } catch (io.jsonwebtoken.JwtException e) {
            // expected (any JWT failure is acceptable)
        }
    }

    /** 8. malformed token is rejected. */
    @Test
    void malformedTokenIsRejected() {
        assertFalse(provider.validateToken("not-a-jwt-token"));
        assertFalse(provider.validateToken(""));
        assertFalse(provider.validateToken(null));
    }

    /** 9. tampered token is rejected. */
    @Test
    void tamperedTokenIsRejected() {
        String token = provider.generateToken(USER_ID);
        // Corrupt the signature segment so verification must fail.
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + ".corrupted";
        assertFalse(provider.validateToken(tampered));
    }

    /** 10. token signed with another key is rejected. */
    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        JwtTokenProvider otherProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(otherProvider, "jwtSecret", OTHER_SECRET);
        ReflectionTestUtils.setField(otherProvider, "jwtExpirationDate", EXPIRATION_MS);
        ReflectionTestUtils.setField(otherProvider, "jwtIssuer", ISSUER);

        String foreignToken = otherProvider.generateToken(USER_ID);

        // The foreign provider validates its own token, but the primary provider
        // must reject it (signature was produced with a different key).
        assertTrue(otherProvider.validateToken(foreignToken));
        assertFalse(provider.validateToken(foreignToken));
    }

    private Claims parseClaims(String token) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET));
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}

