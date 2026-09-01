package com.code.HushChat.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * The SINGLE canonical JWT implementation for the application.
 *
 * <p>All tokens — room tokens, OTP/session tokens — are generated and validated
 * through this provider using the same signing key ({@code app.jwt-secret},
 * Base64-decoded before HMAC key derivation), the same HS256 algorithm, and the
 * same subject identity (userId). This is the result of SECURITY FIX #8, which
 * unified the previously-independent {@code TokenUtil} implementation into this
 * provider.</p>
 *
 * <p>Claim contract after the fix:</p>
 * <ul>
 *   <li>{@code sub} = userId</li>
 *   <li>{@code userId} = userId (preserved for session/OTP tokens)</li>
 *   <li>{@code deviceId} = deviceId when available (session/OTP tokens)</li>
 *   <li>{@code iss} = hush-chat (newly generated tokens)</li>
 *   <li>{@code iat} = issued-at timestamp</li>
 *   <li>{@code exp} = expiration timestamp</li>
 * </ul>
 *
 * <p>Validation: signature and expiration are always verified. Issuer is NOT
 * enforced during validation so that pre-existing room tokens (which predate
 * issuer support and carry no {@code iss} claim) remain accepted. Newly
 * generated tokens always include the issuer.</p>
 */
@Component
public class JwtTokenProvider {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);

    private static final String USER_ID_CLAIM = "userId";
    private static final String DEVICE_ID_CLAIM = "deviceId";

    @Value("${app.jwt-secret}")
    private String jwtSecret;

    @Value("${app.jwt-expiration-milliseconds}")
    private long jwtExpirationDate;

    @Value("${hush.jwt.issuer:hush-chat}")
    private String jwtIssuer;

    /**
     * Generate a canonical JWT for a room/user without a deviceId (room flow).
     *
     * @param userId the authenticated subject (stored in {@code sub} and {@code userId})
     * @return the compact JWT string
     */
    public String generateToken(String userId) {
        Date currentDate = new Date();
        Date expireDate = new Date(currentDate.getTime() + jwtExpirationDate);

        return Jwts.builder()
                .subject(userId)
                .claim(USER_ID_CLAIM, userId)
                .issuer(jwtIssuer)
                .issuedAt(currentDate)
                .expiration(expireDate)
                .signWith(key(), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Generate a canonical JWT for an OTP/session flow, carrying a deviceId.
     *
     * @param userId     the authenticated subject
     * @param deviceId   the device identifier to embed in the token
     * @param expiryTime the exact token expiration time
     * @return the compact JWT string
     */
    public String generateToken(String userId, String deviceId, LocalDateTime expiryTime) {
        Date expiryDate = Date.from(expiryTime.atZone(ZoneId.systemDefault()).toInstant());
        Date currentDate = new Date();

        return Jwts.builder()
                .subject(userId)
                .claim(USER_ID_CLAIM, userId)
                .claim(DEVICE_ID_CLAIM, deviceId)
                .issuer(jwtIssuer)
                .issuedAt(currentDate)
                .expiration(expiryDate)
                .signWith(key(), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Reference implementation signature (subject = authentication name).
     */
    public String generateToken(Authentication authentication) {
        String username = authentication.getName();
        return generateToken(username);
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    }

    /**
     * Alias for {@link #getUserIdFromToken(String)} used by the HTTP filter.
     */
    public String getUsername(String token) {
        return getUserIdFromToken(token);
    }

    /**
     * Extract the subject (userId) from a valid canonical token.
     *
     * @param token the JWT
     * @return the subject (userId)
     * @throws JwtException if the token cannot be parsed/verified
     */
    public String getUserIdFromToken(String token) {
        Claims claims = getClaims(token);
        return claims.getSubject();
    }

    /**
     * Extract the {@code userId} claim from a valid canonical token.
     *
     * @param token the JWT
     * @return the userId claim value, or the subject if the claim is absent
     */
    public String getUserIdClaim(String token) {
        Claims claims = getClaims(token);
        String userId = claims.get(USER_ID_CLAIM, String.class);
        return userId != null ? userId : claims.getSubject();
    }

    /**
     * Extract the {@code deviceId} claim from a valid canonical token.
     *
     * @param token the JWT
     * @return the deviceId, or {@code null} if absent (e.g. room tokens)
     */
    public String getDeviceId(String token) {
        Claims claims = getClaims(token);
        return claims.get(DEVICE_ID_CLAIM, String.class);
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(key())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (MalformedJwtException ex) {
            logger.error("Invalid JWT token: {}", ex.getMessage());
        } catch (ExpiredJwtException ex) {
            logger.error("JWT token is expired: {}", ex.getMessage());
        } catch (UnsupportedJwtException ex) {
            logger.error("JWT token is unsupported: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            logger.error("JWT claims string is empty: {}", ex.getMessage());
        } catch (io.jsonwebtoken.security.SignatureException ex) {
            logger.error("JWT signature does not match: {}", ex.getMessage());
        }
        return false;
    }
}
