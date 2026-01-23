package com.code.HushChat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "hush")
@Data
public class AppConfig {
    
    private OtpConfig otp = new OtpConfig();
    private SessionConfig session = new SessionConfig();
    private MessageConfig message = new MessageConfig();
    private RoomConfig room = new RoomConfig();
    private FileConfig file = new FileConfig();
    private CleanupConfig cleanup = new CleanupConfig();
    private RateLimitConfig rateLimit = new RateLimitConfig();
    private PollingConfig polling = new PollingConfig();
    
    @Data
    public static class OtpConfig {
        private int length = 6;
        private int expiryMinutes = 3;
        private int maxRetries = 3;
    }
    
    @Data
    public static class SessionConfig {
        private int expiryHours = 24;
    }
    
    @Data
    public static class MessageConfig {
        private int ttlMinutes = 10;
    }
    
    @Data
    public static class RoomConfig {
        private int codeLength = 5;
        private int maxUsers = 10;
        private int inactivityTimeoutMinutes = 30;
    }
    
    @Data
    public static class FileConfig {
        private String uploadDir = "./uploads";
        private int maxSizeMb = 200;
        private String allowedTypes = "jpg,jpeg,png,pdf,txt,docx,xlsx";
    }
    
    @Data
    public static class CleanupConfig {
        private int intervalSeconds = 30;
    }
    
    @Data
    public static class RateLimitConfig {
        private int otpPerHour = 5;
        private int messagesPerMinute = 30;
        private int filesPerHour = 10;
    }
    
    @Data
    public static class PollingConfig {
        private int intervalSeconds = 2;
    }

    public int getOtpPerHour() {
        return rateLimit.getOtpPerHour();
    }
}