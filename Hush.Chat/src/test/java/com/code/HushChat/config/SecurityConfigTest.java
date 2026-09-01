package com.code.HushChat.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression test for SECURITY FIX #3: verifies that Spring Boot's public
 * static frontend resources are explicitly permitted anonymously while the
 * deny-by-default rule and API protection remain intact.
 *
 * <p>Verifies:
 * <ul>
 *   <li>GET / and the frontend entry point are publicly accessible</li>
 *   <li>static CSS/JS resources are publicly accessible</li>
 *   <li>an unknown/unmatched endpoint remains denied (deny-by-default)</li>
 *   <li>/api/dev/** is NOT publicly accessible</li>
 *   <li>/api/** protection is not weakened</li>
 * </ul>
 *
 * @since 3.0.0
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "app.jwt-secret=daf66e01593f61a15b857cf433aae03a005812b31234e149036bcc8dee755dbb",
    "app.jwt-expiration-milliseconds=3600000",
    "cors.allowed-origins=http://localhost:8080"
})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    /** Frontend entry point (serves index.html) must be publicly accessible. */
    @Test
    void frontendEntryPointIsPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is(HttpStatus.OK.value()));
        mockMvc.perform(get("/index.html"))
                .andExpect(status().is(HttpStatus.OK.value()));
    }

    /** All static frontend pages must be publicly accessible. */
    @Test
    void frontendPagesArePubliclyAccessible() throws Exception {
        mockMvc.perform(get("/chat.html"))
                .andExpect(status().is(HttpStatus.OK.value()));
        mockMvc.perform(get("/otp.html"))
                .andExpect(status().is(HttpStatus.OK.value()));
    }

    /** Static CSS resources must be publicly accessible. */
    @Test
    void staticCssResourcesArePubliclyAccessible() throws Exception {
        mockMvc.perform(get("/css/common.css"))
                .andExpect(status().is(HttpStatus.OK.value()));
    }

    /** Static JS resources must be publicly accessible. */
    @Test
    void staticJsResourcesArePubliclyAccessible() throws Exception {
        mockMvc.perform(get("/js/config.js"))
                .andExpect(status().is(HttpStatus.OK.value()));
    }

    /** Unknown/unmatched endpoints must remain denied (deny-by-default). */
    @Test
    void unknownEndpointRemainsDenied() throws Exception {
        mockMvc.perform(get("/no-such-resource"))
                .andExpect(status().is(HttpStatus.UNAUTHORIZED.value()));
    }

    /** /api/dev/** must remain NOT publicly accessible. */
    @Test
    void devApiRemainsNotPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/api/dev/anything"))
                .andExpect(status().is(HttpStatus.UNAUTHORIZED.value()));
    }

    /** /api/** protection must not be weakened (unauthenticated requests rejected). */
    @Test
    void apiProtectionIsNotWeakened() throws Exception {
        mockMvc.perform(get("/api/rooms/messages"))
                .andExpect(status().is(HttpStatus.UNAUTHORIZED.value()));
    }
}
