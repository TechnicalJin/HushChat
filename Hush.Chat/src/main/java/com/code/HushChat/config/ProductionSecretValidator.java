package com.code.HushChat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * SECURITY FIX #9A: Validates that critical production secrets have been
 * explicitly provided via environment variables and are not insecure defaults.
 *
 * <p>Only active in the {@code prod} profile. Fails fast at startup if the
 * JWT secret is still the development default, which would compromise token
 * signing security.</p>
 */
@Configuration
@Profile("prod")
@Slf4j
public class ProductionSecretValidator implements CommandLineRunner {

    /**
     * The known default/development JWT secret. If this value is detected in
     * production the application must refuse to start.
     */
    private static final String INSECURE_DEFAULT_JWT_SECRET =
            "daf66e01593f61a15b857cf433aae03a005812b31234e149036bcc8dee755dbb";

    @Value("${app.jwt-secret}")
    private String jwtSecret;

    @Override
    public void run(String... args) {
        if (jwtSecret == null || jwtSecret.trim().isEmpty()) {
            log.error("╔════════════════════════════════════════════════════════════════╗");
            log.error("║  FATAL: APP_JWT_SECRET environment variable is not set!       ║");
            log.error("║  The application cannot start without a secure JWT secret.    ║");
            log.error("╚════════════════════════════════════════════════════════════════╝");
            throw new IllegalStateException(
                    "APP_JWT_SECRET environment variable must be set for production. " +
                    "Generate one with: openssl rand -hex 32");
        }

        if (INSECURE_DEFAULT_JWT_SECRET.equals(jwtSecret.trim())) {
            log.error("╔════════════════════════════════════════════════════════════════╗");
            log.error("║  FATAL: APP_JWT_SECRET is still the development default!      ║");
            log.error("║  Using the default secret in production compromises all       ║");
            log.error("║  JWT token signatures. Generate a new secret with:            ║");
            log.error("║    openssl rand -hex 32                                       ║");
            log.error("╚════════════════════════════════════════════════════════════════╝");
            throw new IllegalStateException(
                    "APP_JWT_SECRET must NOT be the default development value in production. " +
                    "Generate a new one with: openssl rand -hex 32");
        }

        if (jwtSecret.trim().length() < 64) {
            log.warn("APP_JWT_SECRET appears short ({} chars). A 256-bit (64 hex chars) key is recommended.",
                    jwtSecret.trim().length());
        }

        log.info("Production secret validation passed");
    }
}