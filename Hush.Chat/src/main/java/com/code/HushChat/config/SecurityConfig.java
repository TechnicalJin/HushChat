package com.code.HushChat.config;

import com.code.HushChat.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;

/**
 * Spring Security Configuration for JWT-based authentication.
 * 
 * <p>This configuration sets up a stateless REST API security using JWT tokens.
 * It configures public and protected endpoints, disables unnecessary features
 * like CSRF (since we're using JWT), and adds custom JWT authentication filter.</p>
 * 
 * <p>Security Filter Chain Order:
 * <ol>
 *   <li>JwtAuthenticationFilter - Extracts and validates JWT tokens</li>
 *   <li>UsernamePasswordAuthenticationFilter - Standard Spring Security filter (not used but part of chain)</li>
 * </ol>
 * </p>
 * 
 * @author Hush Chat Security Team
 * @since 1.0
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * Configures the security filter chain for the application.
     * 
     * <p>Public endpoints (no authentication required):
     * <ul>
     *   <li>POST /api/auth/register - User registration</li>
     *   <li>POST /api/auth/login - User login</li>
     *   <li>POST /api/rooms/create - Room creation</li>
     *   <li>POST /api/rooms/join - Join room</li>
     *   <li>GET /actuator/health - Health check endpoint</li>
     *   <li>/ws/** - WebSocket endpoints</li>
     *   <li>/ and /index.html - Public frontend entry point</li>
     *   <li>/chat.html, /otp.html - Frontend pages</li>
     *   <li>/css/**, /js/** - Public static frontend resources</li>
     * </ul>
     * </p>
     * 
     * <p>Protected endpoints (authentication required):
     * <ul>
     *   <li>All other /api/** endpoints</li>
     *   <li>/api/dev/** - Dev tools (no longer public)</li>
     * </ul>
     * </p>
     * 
     * <p>Default: deny-by-default for any request not matched above.</p>
     * 
     * @param http the HttpSecurity to configure
     * @return the configured SecurityFilterChain
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF - not needed for stateless JWT-based API
            .csrf(AbstractHttpConfigurer::disable)
            
            // Disable form login - we use JWT tokens instead
            .formLogin(AbstractHttpConfigurer::disable)
            
            // Disable HTTP Basic authentication
            .httpBasic(AbstractHttpConfigurer::disable)
            
            // Configure session management - stateless (no JSESSIONID cookies)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            
            // Configure authorization rules
            .authorizeHttpRequests(auth -> auth
                // Public endpoints - no authentication required
                .requestMatchers("/api/auth/register").permitAll()
                .requestMatchers("/api/auth/login").permitAll()
                .requestMatchers("/api/rooms/create").permitAll()
                .requestMatchers("/api/rooms/join").permitAll()
                .requestMatchers("/api/rooms/leave").permitAll()   // Leave room
                .requestMatchers("/api/rooms/*/info").permitAll()  // Room info
                .requestMatchers("/api/rooms/*/exists").permitAll() // Room exists check
                
                // Reaction defaults are public (no auth needed for static emoji list)
                .requestMatchers("/api/reactions/defaults").permitAll()
                
                // SECURITY FIX #9D: Actuator endpoints - only health is public;
                // all other actuator endpoints (env, configprops, metrics, etc.)
                // require authentication to prevent information leakage.
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/actuator/**").authenticated()

                // Message, file, and reaction mutation/query endpoints require authentication
                .requestMatchers("/api/messages/**").authenticated()
                .requestMatchers("/api/files/**").authenticated()
                .requestMatchers("/api/reactions/**").authenticated()
                .requestMatchers("/ws/**").permitAll()
                
                // Public static frontend resources (Spring Boot serves these from classpath:/static/)
                .requestMatchers("/", "/index.html", "/chat.html", "/otp.html").permitAll()
                .requestMatchers("/css/**").permitAll()
                .requestMatchers("/js/**").permitAll()
                
                // All other /api/** endpoints require authentication
                .requestMatchers("/api/**").authenticated()
                
                // Deny-by-default: any request not matched above is rejected
                .anyRequest().denyAll()
            )
            
            // SECURITY FIX #9C: Configure security response headers
            .headers(headers -> headers
                // Prevent browsers from MIME-sniffing the response content type
                .contentTypeOptions(contentType -> {})
                // Prevent page from being embedded in an iframe (clickjacking)
                .frameOptions(frame -> frame.deny())
                // Enable XSS protection in browsers
                .xssProtection(xss -> xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                // Referrer-Policy: only send origin to cross-origin requests
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                // Content-Security-Policy: restrict resource loading
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; " +
                    "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; " +
                    "style-src 'self' 'unsafe-inline'; " +
                    "img-src 'self' data:; " +
                    "font-src 'self'; " +
                    "connect-src 'self' ws: wss:; " +
                    "frame-ancestors 'none'"
                ))
                // HSTS: tell browsers to only use HTTPS for 1 year (including subdomains)
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000) // 1 year
                    .preload(true)
                )
                // Permissions-Policy: disable unused browser features
                .permissionsPolicy(permissions -> permissions.policy(
                    "camera=(), microphone=(), geolocation=(), payment=()"
                ))
            )

            // Configure exception handling
            .exceptionHandling(exceptions -> exceptions
                // Handle unauthorized requests (401)
                .authenticationEntryPoint((request, response, authException) -> {
                    log.warn("Unauthorized request to: {} - {}", 
                        request.getRequestURI(), authException.getMessage());
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.getWriter().write(
                        "{\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}"
                    );
                })
                
                // Handle forbidden requests (403)
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    log.warn("Forbidden request to: {} - {}", 
                        request.getRequestURI(), accessDeniedException.getMessage());
                    response.setStatus(403);
                    response.setContentType("application/json");
                    response.getWriter().write(
                        "{\"error\":\"Forbidden\",\"message\":\"Access denied\"}"
                    );
                })
            );
        
        // Add JWT authentication filter before UsernamePasswordAuthenticationFilter
        // This ensures JWT validation happens first in the filter chain
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }

    /**
     * Creates a BCryptPasswordEncoder bean for password hashing.
     * 
     * <p>BCrypt is a strong, adaptive hashing function that includes a salt
     * and can be configured to be slower as hardware improves (via the strength parameter).
     * This makes it resistant to brute-force attacks.</p>
     * 
     * @return BCryptPasswordEncoder instance
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
