package com.code.HushChat.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that {@link CorsConfig} is property-driven and does not use a
 * hardcoded origin list or a wildcard.
 *
 * Verifies:
 * - the configured allowed origins are read from {@code cors.allowed-origins}
 * - credentials are allowed alongside explicit origin patterns
 * - no wildcard ("*") origin is configured
 *
 * @since 3.0.0
 */
@SpringBootTest
@TestPropertySource(properties = {
    "cors.allowed-origins=https://app.example.com,http://localhost:8080",
    "app.jwt-secret=daf66e01593f61a15b857cf433aae03a005812b31234e149036bcc8dee755dbb",
    "app.jwt-expiration-milliseconds=3600000"
})
class CorsConfigTest {

    @Autowired
    private UrlBasedCorsConfigurationSource corsConfigurationSource;

    @Test
    void corsOriginsArePropertyDrivenAndNotWildcard() {
        CorsConfiguration corsConfiguration =
                corsConfigurationSource.getCorsConfigurations().get("/api/**");

        assertThat(corsConfiguration).isNotNull();
        // Property-driven origins (no wildcard)
        assertThat(corsConfiguration.getAllowedOriginPatterns())
                .containsExactlyInAnyOrder("https://app.example.com", "http://localhost:8080");
        assertThat(corsConfiguration.getAllowedOriginPatterns())
                .doesNotContain("*");
        // Credentials remain enabled with explicit origins
        assertThat(corsConfiguration.getAllowCredentials()).isTrue();
    }
}
