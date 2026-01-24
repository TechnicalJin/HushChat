package com.code.HushChat.service;

import com.code.HushChat.config.AppConfig;
import com.code.HushChat.model.ChatRoom;
import com.code.HushChat.model.OtpSession;
import com.code.HushChat.model.UserSession;
import com.code.HushChat.storage.InMemoryMessageStore;
import com.code.HushChat.storage.InMemoryOtpStore;
import com.code.HushChat.storage.InMemoryRoomStore;
import com.code.HushChat.storage.InMemorySessionStore;
import com.code.HushChat.util.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CleanupService {
    
    private final InMemoryOtpStore otpStore;
    private final InMemorySessionStore sessionStore;
    private final InMemoryRoomStore roomStore;
    private final InMemoryMessageStore messageStore;
    private final MessageService messageService;
    private final RateLimiter rateLimiter;
    private final AppConfig appConfig;
    
    /**
     * Cleanup expired OTP sessions
     */
    @Scheduled(fixedRateString = "${hush.cleanup.interval-seconds:30}000")
    public void cleanupExpiredOtpSessions() {
        int removed = 0;
        
        for (Map.Entry<String, OtpSession> entry : otpStore.getAll().entrySet()) {
            if (entry.getValue().isExpired()) {
                otpStore.delete(entry.getKey());
                removed++;
            }
        }
        
        if (removed > 0) {
            log.info("Cleanup: removed {} expired OTP sessions", removed);
        }
    }
    
    /**
     * Cleanup expired user sessions
     */
    @Scheduled(fixedRateString = "${hush.cleanup.interval-seconds:30}000")
    public void cleanupExpiredSessions() {
        int removed = 0;
        
        for (Map.Entry<String, UserSession> entry : sessionStore.getAll().entrySet()) {
            if (entry.getValue().isExpired()) {
                sessionStore.delete(entry.getKey());
                removed++;
            }
        }
        
        if (removed > 0) {
            log.info("Cleanup: removed {} expired user sessions", removed);
        }
    }
    
    /**
     * Cleanup expired rooms
     */
    @Scheduled(fixedRateString = "${hush.cleanup.interval-seconds:30}000")
    public void cleanupExpiredRooms() {
        int removed = 0;
        
        for (Map.Entry<String, ChatRoom> entry : roomStore.getAll().entrySet()) {
            if (entry.getValue().isExpired()) {
                String roomCode = entry.getKey();
                roomStore.delete(roomCode);
                // Also clean up messages for this room
                messageStore.deleteRoomMessages(roomCode);
                removed++;
            }
        }
        
        if (removed > 0) {
            log.info("Cleanup: removed {} expired rooms", removed);
        }
    }
    
    /**
     * Cleanup expired messages (10-minute TTL)
     */
    @Scheduled(fixedRateString = "${hush.cleanup.interval-seconds:30}000")
    public void cleanupExpiredMessages() {
        messageService.cleanupExpiredMessages();
    }
    
    /**
     * Cleanup rate limiter entries
     */
    @Scheduled(fixedRateString = "60000") // Every minute
    public void cleanupRateLimiter() {
        rateLimiter.cleanup();
    }
    
    /**
     * Log system stats periodically
     */
    @Scheduled(fixedRateString = "300000") // Every 5 minutes
    public void logSystemStats() {
        int activeSessions = sessionStore.size();
        int activeRooms = roomStore.size();
        int totalMessages = messageStore.getTotalMessageCount();
        int pendingOtps = otpStore.size();
        
        log.info("System Stats - Sessions: {}, Rooms: {}, Messages: {}, Pending OTPs: {}",
                activeSessions, activeRooms, totalMessages, pendingOtps);
    }
}
