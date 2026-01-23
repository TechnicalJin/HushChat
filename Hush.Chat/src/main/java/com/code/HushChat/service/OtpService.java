package com.code.HushChat.service;

import com.code.HushChat.config.AppConfig;
import com.code.HushChat.exception.InvalidOtpException;
import com.code.HushChat.model.OtpSession;
import com.code.HushChat.storage.InMemoryOtpStore;
import com.code.HushChat.util.CodeGenerator;
import com.code.HushChat.util.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {
    
    private final InMemoryOtpStore otpStore;
    private final RateLimiter rateLimiter;
    private final AppConfig appConfig;
    
    /**
     * Generate and send OTP to device
     */
    public OtpSession generateOtp(String deviceId) {
        // Check rate limiting
        if (!rateLimiter.isOtpRequestAllowed(deviceId, appConfig.getOtpPerHour())) {
            int remaining = rateLimiter.getRemainingOtpRequests(deviceId, 
                appConfig.getOtpPerHour());
            throw new InvalidOtpException(
                "Rate limit exceeded. Try again later. Remaining requests: " + remaining);
        }
        
        // Generate OTP
        String otp = CodeGenerator.generateOtp(appConfig.getOtp().getLength());
        
        LocalDateTime expiryTime = LocalDateTime.now()
            .plusMinutes(appConfig.getOtp().getExpiryMinutes());
        
        OtpSession session = OtpSession.builder()
            .deviceId(deviceId)
            .otp(otp)
            .expiryTime(expiryTime)
            .retryCount(0)
            .createdAt(LocalDateTime.now())
            .build();
        
        // Store OTP
        otpStore.save(deviceId, session);
        
        // In production, send OTP via SMS/Email
        log.info("OTP generated for device: {} (expires in {} minutes)", 
            deviceId.substring(0, Math.min(8, deviceId.length())) + "...", 
            appConfig.getOtp().getExpiryMinutes());
        
        // For development/testing, log the OTP (REMOVE IN PRODUCTION)
        log.debug("🔐 OTP for {}: {}", deviceId, otp);
        
        return session;
    }
    
    /**
     * Verify OTP for device
     */
    public boolean verifyOtp(String deviceId, String otp) {
        OtpSession session = otpStore.get(deviceId);
        
        if (session == null) {
            throw new InvalidOtpException("No OTP found for this device. Please request a new OTP.");
        }
        
        // Check if expired
        if (session.isExpired()) {
            otpStore.delete(deviceId);
            throw new InvalidOtpException("OTP has expired. Please request a new one.");
        }
        
        // Check retry limit
        if (!session.hasRetriesLeft(appConfig.getOtp().getMaxRetries())) {
            otpStore.delete(deviceId);
            throw new InvalidOtpException(
                "Maximum retry attempts exceeded. Please request a new OTP.");
        }
        
        // Verify OTP
        if (!session.getOtp().equals(otp)) {
            // Increment retry count
            session.setRetryCount(session.getRetryCount() + 1);
            otpStore.save(deviceId, session);
            
            int retriesLeft = appConfig.getOtp().getMaxRetries() - session.getRetryCount();
            throw new InvalidOtpException(
                "Invalid OTP. " + retriesLeft + " attempt(s) remaining.");
        }
        
        // OTP verified successfully - remove it
        otpStore.delete(deviceId);
        log.info("OTP verified successfully for device: {}", 
            deviceId.substring(0, Math.min(8, deviceId.length())) + "...");
        
        return true;
    }
    
    /**
     * Get remaining retries for device
     */
    public int getRemainingRetries(String deviceId) {
        OtpSession session = otpStore.get(deviceId);
        if (session == null) {
            return appConfig.getOtp().getMaxRetries();
        }
        return appConfig.getOtp().getMaxRetries() - session.getRetryCount();
    }
    
    /**
     * Check if OTP exists and is valid for device
     */
    public boolean hasValidOtp(String deviceId) {
        OtpSession session = otpStore.get(deviceId);
        return session != null && !session.isExpired();
    }
    
    /**
     * Manually invalidate OTP for device
     */
    public void invalidateOtp(String deviceId) {
        otpStore.delete(deviceId);
        log.info("OTP invalidated for device: {}", 
            deviceId.substring(0, Math.min(8, deviceId.length())) + "...");
    }
}