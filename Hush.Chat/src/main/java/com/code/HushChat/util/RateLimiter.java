package com.code.HushChat.util;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimiter {
    
    // deviceId -> RateLimitInfo
    private final Map<String, RateLimitInfo> otpRateLimits = new ConcurrentHashMap<>();
    private final Map<String, RateLimitInfo> messageRateLimits = new ConcurrentHashMap<>();
    private final Map<String, RateLimitInfo> fileRateLimits = new ConcurrentHashMap<>();
    
    /**
     * Check if OTP request is allowed for device
     */
    public boolean isOtpRequestAllowed(String deviceId, int maxRequestsPerHour) {
        return isAllowed(otpRateLimits, deviceId, maxRequestsPerHour, 60);
    }
    
    /**
     * Check if message send is allowed for user
     */
    public boolean isMessageAllowed(String userId, int maxMessagesPerMinute) {
        return isAllowed(messageRateLimits, userId, maxMessagesPerMinute, 1);
    }
    
    /**
     * Check if file upload is allowed for user
     */
    public boolean isFileUploadAllowed(String userId, int maxUploadsPerHour) {
        return isAllowed(fileRateLimits, userId, maxUploadsPerHour, 60);
    }
    
    /**
     * Generic rate limiting logic
     */
    private boolean isAllowed(Map<String, RateLimitInfo> rateLimits, String key, 
                              int maxRequests, int windowMinutes) {
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = now.minusMinutes(windowMinutes);
        
        RateLimitInfo info = rateLimits.computeIfAbsent(key, k -> new RateLimitInfo());
        
        // Remove old requests outside the time window
        info.requestTimes.removeIf(time -> time.isBefore(windowStart));
        
        // Check if limit exceeded
        if (info.requestTimes.size() >= maxRequests) {
            return false;
        }
        
        // Add current request
        info.requestTimes.add(now);
        return true;
    }
    
    /**
     * Get remaining OTP requests for device
     */
    public int getRemainingOtpRequests(String deviceId, int maxRequestsPerHour) {
        RateLimitInfo info = otpRateLimits.get(deviceId);
        if (info == null) {
            return maxRequestsPerHour;
        }
        
        LocalDateTime windowStart = LocalDateTime.now().minusMinutes(60);
        long recentRequests = info.requestTimes.stream()
                .filter(time -> time.isAfter(windowStart))
                .count();
        
        return Math.max(0, maxRequestsPerHour - (int) recentRequests);
    }
    
    /**
     * Clear rate limits for a key (useful for testing or manual reset)
     */
    public void clearOtpRateLimit(String deviceId) {
        otpRateLimits.remove(deviceId);
    }
    
    public void clearMessageRateLimit(String userId) {
        messageRateLimits.remove(userId);
    }
    
    public void clearFileRateLimit(String userId) {
        fileRateLimits.remove(userId);
    }
    
    /**
     * Clean up old rate limit entries
     */
    public void cleanup() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(2);
        
        cleanupMap(otpRateLimits, cutoff);
        cleanupMap(messageRateLimits, cutoff);
        cleanupMap(fileRateLimits, cutoff);
    }
    
    private void cleanupMap(Map<String, RateLimitInfo> map, LocalDateTime cutoff) {
        map.entrySet().removeIf(entry -> {
            entry.getValue().requestTimes.removeIf(time -> time.isBefore(cutoff));
            return entry.getValue().requestTimes.isEmpty();
        });
    }
    
    /**
     * Inner class to track rate limit info
     */
    private static class RateLimitInfo {
        private final java.util.List<LocalDateTime> requestTimes = 
            new java.util.concurrent.CopyOnWriteArrayList<>();
    }
}