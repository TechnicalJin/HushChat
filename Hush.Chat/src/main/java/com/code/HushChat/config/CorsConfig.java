package com.code.HushChat.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * HTTP CORS configuration.
 *
 * Allowed origins are property-driven via the {@code cors.allowed-origins}
 * application property (comma-separated). Using {@code setAllowedOriginPatterns()}
 * allows credentials together with explicitly configured origin patterns while
 * never using a wildcard.
 */
@Configuration
public class CorsConfig {

    /**
     * Comma-separated list of allowed origins, injected from the
     * {@code cors.allowed-origins} application property.
     */
    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Property-driven allowed origins (no hardcoded localhost list)
        config.setAllowedOriginPatterns(parseAllowedOrigins());

        // Allow credentials only with explicit configured origins/patterns
        config.setAllowCredentials(true);

        // Allow specific headers
        config.setAllowedHeaders(Arrays.asList(
            "Origin",
            "Content-Type",
            "Accept",
            "Authorization",
            "X-Requested-With",
            "X-Device-Id"
        ));

        // Allow specific methods
        config.setAllowedMethods(Arrays.asList(
            "GET",
            "POST",
            "PUT",
            "DELETE",
            "OPTIONS"
        ));

        // Max age for preflight
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);

        return source;
    }

    @Bean
    public CorsFilter corsFilter(CorsConfigurationSource corsConfigurationSource) {
        return new CorsFilter(corsConfigurationSource);
    }

    /**
     * Parse the comma-separated {@code cors.allowed-origins} property into a
     * list of origin patterns, trimming whitespace and dropping empty entries.
     *
     * The property is required; no wildcard fallback is provided.
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

