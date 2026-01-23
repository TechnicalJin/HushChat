package com.code.HushChat.controller;

import com.code.HushChat.config.AppConfig;
import com.code.HushChat.dto.OtpRequestDto;
import com.code.HushChat.dto.OtpResponseDto;
import com.code.HushChat.dto.OtpVerifyDto;
import com.code.HushChat.dto.SessionResponseDto;
import com.code.HushChat.model.ApiResponse;
import com.code.HushChat.model.OtpSession;
import com.code.HushChat.model.UserSession;
import com.code.HushChat.service.OtpService;
import com.code.HushChat.service.SessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    
    private final OtpService otpService;
    private final SessionService sessionService;
    private final AppConfig appConfig;
    
    /**
     * Request OTP for device
     * POST /api/auth/request-otp
     */
    @PostMapping("/request-otp")
    public ResponseEntity<ApiResponse<OtpResponseDto>> requestOtp(
            @Valid @RequestBody OtpRequestDto request) {
        
        log.info("OTP request received for device: {}", 
            request.getDeviceId().substring(0, Math.min(8, request.getDeviceId().length())) + "...");
        
        OtpSession otpSession = otpService.generateOtp(request.getDeviceId());
        
        OtpResponseDto response = OtpResponseDto.builder()
            .message("OTP sent successfully. Please check your device.")
            .expiryMinutes(appConfig.getOtp().getExpiryMinutes())
            .retriesLeft(appConfig.getOtp().getMaxRetries())
            .expiryTime(otpSession.getExpiryTime())
            .build();
        
        return ResponseEntity
            .status(HttpStatus.OK)
            .body(ApiResponse.success("OTP generated successfully", response));
    }
    
    /**
     * Verify OTP and create session
     * POST /api/auth/verify-otp
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<SessionResponseDto>> verifyOtp(
            @Valid @RequestBody OtpVerifyDto request) {
        
        log.info("OTP verification attempt for device: {}", 
            request.getDeviceId().substring(0, Math.min(8, request.getDeviceId().length())) + "...");
        
        // Verify OTP
        otpService.verifyOtp(request.getDeviceId(), request.getOtp());
        
        // Create session
        UserSession session = sessionService.createSession(request.getDeviceId());
        
        SessionResponseDto response = SessionResponseDto.builder()
            .sessionToken(session.getSessionToken())
            .userId(session.getUserId())
            .expiryTime(session.getExpiryTime())
            .message("Authentication successful")
            .build();
        
        return ResponseEntity
            .status(HttpStatus.OK)
            .body(ApiResponse.success("Login successful", response));
    }
    
    /**
     * Logout and invalidate session
     * POST /api/auth/logout
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Invalid authorization header"));
        }
        
        String sessionToken = authHeader.substring(7);
        
        sessionService.logout(sessionToken);
        
        return ResponseEntity
            .status(HttpStatus.OK)
            .body(ApiResponse.success("Logged out successfully", null));
    }
    
    /**
     * Validate current session
     * GET /api/auth/validate
     */
    @GetMapping("/validate")
    public ResponseEntity<ApiResponse<SessionResponseDto>> validateSession(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Invalid authorization header"));
        }
        
        String sessionToken = authHeader.substring(7);
        
        UserSession session = sessionService.validateSession(sessionToken);
        
        SessionResponseDto response = SessionResponseDto.builder()
            .sessionToken(sessionToken)
            .userId(session.getUserId())
            .expiryTime(session.getExpiryTime())
            .message("Session is valid")
            .build();
        
        return ResponseEntity
            .status(HttpStatus.OK)
            .body(ApiResponse.success("Session validated", response));
    }
    
    /**
     * Refresh session expiry
     * POST /api/auth/refresh
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<SessionResponseDto>> refreshSession(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Invalid authorization header"));
        }
        
        String sessionToken = authHeader.substring(7);
        
        UserSession session = sessionService.refreshSession(sessionToken);
        
        SessionResponseDto response = SessionResponseDto.builder()
            .sessionToken(sessionToken)
            .userId(session.getUserId())
            .expiryTime(session.getExpiryTime())
            .message("Session refreshed")
            .build();
        
        return ResponseEntity
            .status(HttpStatus.OK)
            .body(ApiResponse.success("Session refreshed successfully", response));
    }
    
    /**
     * Get OTP status for device
     * GET /api/auth/otp-status?deviceId=xxx
     */
    @GetMapping("/otp-status")
    public ResponseEntity<ApiResponse<OtpResponseDto>> getOtpStatus(
            @RequestParam String deviceId) {
        
        boolean hasValidOtp = otpService.hasValidOtp(deviceId);
        int retriesLeft = otpService.getRemainingRetries(deviceId);
        
        OtpResponseDto response = OtpResponseDto.builder()
            .message(hasValidOtp ? "Valid OTP exists" : "No valid OTP")
            .retriesLeft(retriesLeft)
            .expiryMinutes(appConfig.getOtp().getExpiryMinutes())
            .build();
        
        return ResponseEntity
            .status(HttpStatus.OK)
            .body(ApiResponse.success(response));
    }
}