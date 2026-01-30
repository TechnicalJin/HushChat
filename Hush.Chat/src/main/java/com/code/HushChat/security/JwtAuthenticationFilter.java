package com.code.HushChat.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;

/**
 * JWT Authentication Filter for Spring Security.
 * This filter intercepts incoming HTTP requests, extracts JWT tokens from the
 * Authorization header, validates them, and sets up the Spring Security context
 * if the token is valid.
 * 
 * <p>The filter extends {@link OncePerRequestFilter} to ensure it's executed
 * only once per request, even in case of request forwarding.</p>
 * 
 * @author Hush Chat Security Team
 * @since 1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    
    /**
     * Main filter logic that processes each HTTP request.
     * 
     * <p>This method:
     * <ol>
     *   <li>Extracts the JWT token from the Authorization header</li>
     *   <li>Validates the token using JwtTokenProvider</li>
     *   <li>Extracts the user ID from the token if valid</li>
     *   <li>Creates and sets authentication in SecurityContext</li>
     *   <li>Continues the filter chain</li>
     * </ol>
     * </p>
     * 
     * @param request the HTTP request
     * @param response the HTTP response
     * @param filterChain the filter chain to continue
     * @throws ServletException if a servlet error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        
        try {
            // Extract JWT token from Authorization header
            String jwt = extractJwtFromRequest(request);
            
            // If no token found, continue without authentication
            if (jwt == null) {
                log.debug("No JWT token found in request to: {}", request.getRequestURI());
                filterChain.doFilter(request, response);
                return;
            }
            
            // Validate the token
            if (!jwtTokenProvider.validateToken(jwt)) {
                log.debug("Invalid JWT token for request to: {}", request.getRequestURI());
                filterChain.doFilter(request, response);
                return;
            }
            
            // Extract user ID from token
            String userId = jwtTokenProvider.getUserIdFromToken(jwt);
            
            if (userId == null) {
                log.debug("Could not extract userId from JWT token for request to: {}", request.getRequestURI());
                filterChain.doFilter(request, response);
                return;
            }
            
            // Create authentication token
            // Using empty authorities list since we don't have role-based access control yet
            UsernamePasswordAuthenticationToken authentication = 
                    new UsernamePasswordAuthenticationToken(
                            userId, 
                            null, 
                            new ArrayList<>()
                    );
            
            // Set additional details from the request
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            
            // Set authentication in SecurityContext
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            log.debug("Successfully authenticated user: {} for request to: {}", 
                    userId, request.getRequestURI());
            
        } catch (Exception e) {
            // Never throw exceptions from filter - log and continue
            log.error("Cannot set user authentication in security context for request to: {}", 
                    request.getRequestURI(), e);
        }
        
        // Continue the filter chain
        filterChain.doFilter(request, response);
    }
    
    /**
     * Extracts the JWT token from the Authorization header.
     * 
     * <p>The Authorization header must be in the format: "Bearer {token}"</p>
     * 
     * @param request the HTTP request
     * @return the JWT token if present and properly formatted, null otherwise
     */
    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        
        return null;
    }
}
