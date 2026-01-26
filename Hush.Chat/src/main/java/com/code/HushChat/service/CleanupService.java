package com.code.HushChat.service;

import com.code.HushChat.config.AppConfig;
import com.code.HushChat.model.ChatRoom;
import com.code.HushChat.model.FileMetadata;
import com.code.HushChat.model.OtpSession;
import com.code.HushChat.model.UserSession;
import com.code.HushChat.storage.InMemoryFileStore;
import com.code.HushChat.storage.InMemoryMessageStore;
import com.code.HushChat.storage.InMemoryOtpStore;
import com.code.HushChat.storage.InMemoryRoomStore;
import com.code.HushChat.storage.InMemorySessionStore;
import com.code.HushChat.util.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Centralized cleanup service for all ephemeral data.
 * Ensures privacy guarantees by automatically removing expired data.
 * 
 * Cleanup responsibilities:
 * - OTP sessions (expired after configured minutes)
 * - User sessions (expired after configured hours)
 * - Chat messages (TTL-based, default 10 minutes)
 * - Chat rooms (inactive after configured timeout)
 * - Uploaded files (when room expires or file expires)
 * 
 * PRIVACY: Never logs sensitive data (OTP values, message content)
 * Only logs metadata (IDs, counts, timestamps)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CleanupService {
    
    private final InMemoryOtpStore otpStore;
    private final InMemorySessionStore sessionStore;
    private final InMemoryRoomStore roomStore;
    private final InMemoryMessageStore messageStore;
    private final InMemoryFileStore fileStore;
    private final MessageService messageService;
    private final RateLimiter rateLimiter;
    private final AppConfig appConfig;
    
    /**
     * Master cleanup task - runs all cleanup operations in a single scheduled execution.
     * This ensures coordinated cleanup and proper ordering (rooms before orphan files).
     * 
     * Execution order:
     * 1. Expired OTP sessions
     * 2. Expired user sessions
     * 3. Expired rooms (includes room messages and files)
     * 4. Expired messages (TTL-based)
     * 5. Expired files (orphaned or expired)
     */
    @Scheduled(fixedRateString = "${hush.cleanup.interval-seconds:30}000")
    public void performScheduledCleanup() {
        LocalDateTime cleanupStartTime = LocalDateTime.now();
        log.debug("Starting scheduled cleanup cycle at {}", cleanupStartTime);
        
        try {
            int otpsCleaned = cleanupExpiredOtpSessions();
            int sessionsCleaned = cleanupExpiredSessions();
            CleanupResult roomResult = cleanupExpiredRooms();
            int messagesCleaned = cleanupExpiredMessages();
            int filesCleaned = cleanupExpiredFiles();
            
            int totalCleaned = otpsCleaned + sessionsCleaned + 
                               roomResult.roomsRemoved + messagesCleaned + filesCleaned;
            
            if (totalCleaned > 0) {
                log.info("Cleanup cycle completed - OTPs: {}, Sessions: {}, Rooms: {}, " +
                         "Messages: {}, Files: {} (disk: {})", 
                         otpsCleaned, sessionsCleaned, roomResult.roomsRemoved, 
                         messagesCleaned, filesCleaned, roomResult.filesDeletedFromDisk);
            }
        } catch (Exception e) {
            log.error("Error during cleanup cycle: {}", e.getMessage());
        }
    }
    
    /**
     * Cleanup expired OTP sessions.
     * Privacy: Does NOT log OTP values, only counts.
     * 
     * @return number of expired OTP sessions removed
     */
    public int cleanupExpiredOtpSessions() {
        int removed = 0;
        
        // Safe iteration over copy of entries
        for (Map.Entry<String, OtpSession> entry : otpStore.getAll().entrySet()) {
            if (entry.getValue().isExpired()) {
                otpStore.delete(entry.getKey());
                removed++;
            }
        }
        
        if (removed > 0) {
            log.debug("Removed {} expired OTP sessions", removed);
        }
        
        return removed;
    }
    
    /**
     * Cleanup expired user sessions.
     * 
     * @return number of expired sessions removed
     */
    public int cleanupExpiredSessions() {
        int removed = 0;
        
        for (Map.Entry<String, UserSession> entry : sessionStore.getAll().entrySet()) {
            if (entry.getValue().isExpired()) {
                String sessionToken = entry.getKey();
                sessionStore.delete(sessionToken);
                removed++;
                log.debug("Removed expired session: {}", maskToken(sessionToken));
            }
        }
        
        if (removed > 0) {
            log.debug("Removed {} expired user sessions", removed);
        }
        
        return removed;
    }
    
    /**
     * Cleanup expired/inactive rooms.
     * When a room is destroyed:
     * - All messages belonging to that room are removed
     * - All files belonging to that room are removed (from memory AND disk)
     * 
     * @return CleanupResult with rooms and files removed counts
     */
    public CleanupResult cleanupExpiredRooms() {
        int roomsRemoved = 0;
        int messagesRemoved = 0;
        int filesRemovedFromMemory = 0;
        int filesDeletedFromDisk = 0;
        
        for (Map.Entry<String, ChatRoom> entry : roomStore.getAll().entrySet()) {
            ChatRoom room = entry.getValue();
            
            if (room.isExpired()) {
                String roomCode = entry.getKey();
                
                // First, delete all files for this room from disk
                int diskDeleted = deleteRoomFilesFromDisk(roomCode);
                filesDeletedFromDisk += diskDeleted;
                
                // Remove files from memory store
                Set<String> roomFileIds = fileStore.getRoomFiles(roomCode);
                filesRemovedFromMemory += roomFileIds.size();
                fileStore.deleteRoomFiles(roomCode);
                
                // Remove all messages for this room
                int msgCount = messageStore.getRoomMessageCount(roomCode);
                messageStore.deleteRoomMessages(roomCode);
                messagesRemoved += msgCount;
                
                // Remove the room
                roomStore.delete(roomCode);
                roomsRemoved++;
                
                log.info("Cleaned up expired room {} - Messages: {}, Files: {} (disk: {})",
                        roomCode, msgCount, roomFileIds.size(), diskDeleted);
            }
        }
        
        return new CleanupResult(roomsRemoved, messagesRemoved, 
                                 filesRemovedFromMemory, filesDeletedFromDisk);
    }
    
    /**
     * Cleanup expired messages based on TTL.
     * Privacy: Does NOT log message content, only counts.
     * 
     * @return number of expired messages removed
     */
    public int cleanupExpiredMessages() {
        return messageService.cleanupExpiredMessages();
    }
    
    /**
     * Cleanup expired files.
     * - Removes expired file metadata from memory
     * - Deletes expired files from disk
     * - Handles IO errors gracefully (no crash)
     * 
     * @return number of expired files removed
     */
    public int cleanupExpiredFiles() {
        int removed = 0;
        List<String> fileIdsToRemove = new ArrayList<>();
        
        // Collect expired files (safe iteration)
        for (Map.Entry<String, FileMetadata> entry : fileStore.getAll().entrySet()) {
            FileMetadata metadata = entry.getValue();
            
            if (metadata.isExpired()) {
                fileIdsToRemove.add(entry.getKey());
            }
        }
        
        // Remove expired files
        for (String fileId : fileIdsToRemove) {
            FileMetadata metadata = fileStore.get(fileId);
            if (metadata != null) {
                // Delete from disk first
                deleteFileFromDisk(metadata.getFilePath());
                
                // Remove from memory
                fileStore.delete(fileId);
                removed++;
                
                log.debug("Removed expired file: {} (room: {})", 
                         fileId, metadata.getRoomCode());
            }
        }
        
        if (removed > 0) {
            log.debug("Removed {} expired files", removed);
        }
        
        return removed;
    }
    
    /**
     * Delete all files belonging to a room from disk.
     * Called when a room expires or is destroyed.
     * 
     * @param roomCode the room whose files should be deleted
     * @return number of files successfully deleted from disk
     */
    public int deleteRoomFilesFromDisk(String roomCode) {
        int deleted = 0;
        Set<String> fileIds = fileStore.getRoomFiles(roomCode);
        
        for (String fileId : fileIds) {
            FileMetadata metadata = fileStore.get(fileId);
            if (metadata != null && metadata.getFilePath() != null) {
                if (deleteFileFromDisk(metadata.getFilePath())) {
                    deleted++;
                }
            }
        }
        
        return deleted;
    }
    
    /**
     * Delete a single file from disk.
     * Handles IO errors gracefully.
     * 
     * @param filePath the path to the file to delete
     * @return true if file was deleted or didn't exist, false on error
     */
    public boolean deleteFileFromDisk(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return true;
        }
        
        try {
            Path path = Paths.get(filePath);
            if (Files.exists(path)) {
                Files.delete(path);
                log.debug("Deleted file from disk: {}", filePath);
                return true;
            }
            return true; // File doesn't exist, consider it deleted
        } catch (IOException e) {
            log.warn("Failed to delete file from disk: {} - {}", filePath, e.getMessage());
            return false;
        }
    }
    
    /**
     * Cleanup orphan files that have no associated room or metadata.
     * Scans the upload directory for files not tracked in memory.
     * Called less frequently to avoid excessive disk I/O.
     */
    @Scheduled(fixedRateString = "300000") // Every 5 minutes
    public void cleanupOrphanFiles() {
        String uploadDir = appConfig.getFile().getUploadDir();
        Path uploadPath = Paths.get(uploadDir);
        
        if (!Files.exists(uploadPath) || !Files.isDirectory(uploadPath)) {
            return;
        }
        
        int orphansRemoved = 0;
        
        try {
            // Get all tracked file paths
            Set<String> trackedPaths = new java.util.HashSet<>();
            for (FileMetadata metadata : fileStore.getAll().values()) {
                if (metadata.getFilePath() != null) {
                    trackedPaths.add(Paths.get(metadata.getFilePath()).toAbsolutePath().toString());
                }
            }
            
            // Scan upload directory
            try (var stream = Files.list(uploadPath)) {
                for (Path file : stream.toList()) {
                    if (Files.isRegularFile(file)) {
                        String absolutePath = file.toAbsolutePath().toString();
                        if (!trackedPaths.contains(absolutePath)) {
                            // This is an orphan file
                            try {
                                Files.delete(file);
                                orphansRemoved++;
                                log.debug("Removed orphan file: {}", file.getFileName());
                            } catch (IOException e) {
                                log.warn("Failed to delete orphan file: {}", file.getFileName());
                            }
                        }
                    }
                }
            }
            
            if (orphansRemoved > 0) {
                log.info("Removed {} orphan files from upload directory", orphansRemoved);
            }
            
        } catch (IOException e) {
            log.warn("Failed to scan upload directory for orphans: {}", e.getMessage());
        }
    }
    
    /**
     * Cleanup rate limiter entries.
     */
    @Scheduled(fixedRateString = "60000") // Every minute
    public void cleanupRateLimiter() {
        rateLimiter.cleanup();
    }
    
    /**
     * Log system stats periodically.
     * Privacy: Only logs aggregate counts, never sensitive data.
     */
    @Scheduled(fixedRateString = "300000") // Every 5 minutes
    public void logSystemStats() {
        int activeSessions = sessionStore.size();
        int activeRooms = roomStore.size();
        int totalMessages = messageStore.getTotalMessageCount();
        int pendingOtps = otpStore.size();
        int totalFiles = fileStore.size();
        
        log.info("System Stats - Sessions: {}, Rooms: {}, Messages: {}, Files: {}, Pending OTPs: {}",
                activeSessions, activeRooms, totalMessages, totalFiles, pendingOtps);
    }
    
    /**
     * Mask a session token for logging (show only first 8 chars).
     * Privacy: Never log full tokens.
     */
    private String maskToken(String token) {
        if (token == null || token.length() <= 8) {
            return "***";
        }
        return token.substring(0, 8) + "...";
    }
    
    /**
     * Result holder for room cleanup operations.
     */
    public static class CleanupResult {
        public final int roomsRemoved;
        public final int messagesRemoved;
        public final int filesRemovedFromMemory;
        public final int filesDeletedFromDisk;
        
        public CleanupResult(int roomsRemoved, int messagesRemoved, 
                            int filesRemovedFromMemory, int filesDeletedFromDisk) {
            this.roomsRemoved = roomsRemoved;
            this.messagesRemoved = messagesRemoved;
            this.filesRemovedFromMemory = filesRemovedFromMemory;
            this.filesDeletedFromDisk = filesDeletedFromDisk;
        }
    }
}
