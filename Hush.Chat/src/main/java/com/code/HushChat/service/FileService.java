package com.code.HushChat.service;

import com.code.HushChat.config.AppConfig;
import com.code.HushChat.dto.FileUploadResponseDto;
import com.code.HushChat.dto.MessageResponseDto;
import com.code.HushChat.dto.WebSocketEventDto;
import com.code.HushChat.exception.RoomNotFoundException;
import com.code.HushChat.exception.UnauthorizedException;
import com.code.HushChat.model.ChatMessage;
import com.code.HushChat.model.ChatRoom;
import com.code.HushChat.model.FileMetadata;
import com.code.HushChat.model.event.BaseRealtimeEvent;
import com.code.HushChat.model.event.RealtimeEvent;
import com.code.HushChat.realtime.RealtimeDispatcher;
import com.code.HushChat.storage.InMemoryFileStore;
import com.code.HushChat.storage.InMemoryMessageStore;
import com.code.HushChat.storage.InMemoryRoomStore;
import com.code.HushChat.util.FileUtil;
import com.code.HushChat.util.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.UUID;

// NOTE: Long polling (PollEventDto) intentionally removed.
// All real-time communication now uses WebSocket-only transport.

@Service
@RequiredArgsConstructor
@Slf4j
public class FileService {

    private final InMemoryFileStore fileStore;
    private final InMemoryRoomStore roomStore;
    private final InMemoryMessageStore messageStore;
    private final RoomService roomService;
    private final AppConfig appConfig;
    private final RateLimiter rateLimiter;
    private final RealtimeDispatcher realtimeDispatcher;

    /**
     * Upload a file for a room and return metadata + download URL.
     */
    public static String buildRelativeFileDownloadUrl(String fileId) {
        return "/api/files/" + fileId + "/download";
    }

    public FileUploadResponseDto uploadFile(String roomCode,
                                            String userId,
                                            String senderName,
                                            MultipartFile file,
                                            String baseDownloadUrl) throws IOException {

        // Validate room
        ChatRoom room = roomStore.get(roomCode);
        if (room == null || room.isExpired()) {
            throw new RoomNotFoundException("Room not found or expired: " + roomCode);
        }

        // Validate user is in room
        if (!room.getActiveUserIds().contains(userId)) {
            throw new UnauthorizedException("User is not a member of this room");
        }

        // Rate limit uploads per user
        int maxUploadsPerHour = appConfig.getRateLimit().getFilesPerHour();
        if (!rateLimiter.isFileUploadAllowed(userId, maxUploadsPerHour)) {
            throw new UnauthorizedException("File upload rate limit exceeded. Try again later.");
        }

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }

        String originalFilename = file.getOriginalFilename();
        long sizeBytes = file.getSize();

        // SECURITY FIX #9B: Reject dangerous executable extensions first
        if (FileUtil.isDangerousExtension(originalFilename)) {
            throw new IllegalArgumentException(
                "Executable file type is not allowed for upload");
        }

        // Validate size
        long maxSizeBytes = appConfig.getFile().getMaxSizeMb() * 1024L * 1024L;
        if (sizeBytes > maxSizeBytes) {
            throw new IllegalArgumentException("File size exceeds limit of " +
                    appConfig.getFile().getMaxSizeMb() + "MB");
        }

        // Validate type
        if (!FileUtil.isValidFileType(originalFilename, appConfig.getFile().getAllowedTypes())) {
            throw new IllegalArgumentException("File type is not allowed");
        }

        // SECURITY FIX #9B: Validate MIME type consistency with extension
        String declaredContentType = file.getContentType();
        if (declaredContentType != null && !declaredContentType.isBlank()
                && !FileUtil.isMimeTypeConsistent(originalFilename, declaredContentType)) {
            log.warn("MIME type mismatch for upload: filename='{}', declaredContentType='{}'",
                    originalFilename, declaredContentType);
            throw new IllegalArgumentException(
                "File content type does not match the file extension");
        }

        // SECURITY FIX #9B: Sanitize the filename (path traversal, null bytes, etc.)
        String safeOriginal = FileUtil.sanitizeFilename(originalFilename);

        // Ensure upload directory exists
        String uploadDir = appConfig.getFile().getUploadDir();
        FileUtil.createDirectoryIfNotExists(uploadDir);

        // Generate stored filename and path
        String fileId = UUID.randomUUID().toString();
        String storedFilename = fileId + "_" + safeOriginal;

        Path targetPath = Paths.get(uploadDir).resolve(storedFilename).toAbsolutePath();

        // SECURITY FIX #9B: Ensure resolved path stays within upload directory
        Path uploadDirPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (!targetPath.startsWith(uploadDirPath)) {
            throw new IllegalArgumentException("Invalid file path");
        }

        Files.copy(file.getInputStream(), targetPath);

        LocalDateTime uploadTime = LocalDateTime.now();
        // Use same TTL as messages for auto-expire semantics
        LocalDateTime expiryTime = uploadTime.plusMinutes(appConfig.getMessage().getTtlMinutes());

        // Build metadata and save in memory
        FileMetadata metadata = FileMetadata.builder()
                .fileId(fileId)
                .roomCode(roomCode)
                .userId(userId)
                .originalFilename(originalFilename)
                .storedFilename(storedFilename)
                .contentType(file.getContentType())
                .fileSize(sizeBytes)
                .filePath(targetPath.toString())
                .uploadTime(uploadTime)
                .expiryTime(expiryTime)
                .build();

        fileStore.save(fileId, metadata);

        // Create a chat message representing this file so all room members see it
        ChatMessage message = ChatMessage.builder()
                .messageId(fileId) // Use fileId as messageId to keep them in sync
                .roomCode(roomCode)
                .userId(userId)
                .content(originalFilename)
                .type(ChatMessage.MessageType.FILE)
                .timestamp(uploadTime)
                .expiryTime(expiryTime)
                .fileId(fileId)
                .edited(false)
                .deleted(false)
                .lastModified(uploadTime)
                .build();

        messageStore.save(roomCode, message);
        roomService.updateRoomActivity(roomCode);

        String downloadUrl = buildRelativeFileDownloadUrl(fileId);

        log.info("File uploaded - id: {}, room: {}, user: {}, name: {}, size: {} bytes",
                fileId, roomCode, userId, originalFilename, sizeBytes);

        // Broadcast file message to all users in the room via WebSocket
        dispatchFileMessageEvent(roomCode, message, senderName, downloadUrl);

        return FileUploadResponseDto.builder()
                .fileId(fileId)
                .roomCode(roomCode)
                .senderId(userId)
                .senderName(senderName)
                .originalFilename(originalFilename)
                .contentType(file.getContentType())
                .fileSize(sizeBytes)
                .downloadUrl(downloadUrl)
                .uploadTime(uploadTime)
                .expiryTime(expiryTime)
                .build();
    }

    /**
     * Get file metadata by id.
     */
    public FileMetadata getMetadata(String fileId) {
        return fileStore.get(fileId);
    }

    /**
     * Validate access to a file: room must exist, file not expired.
     */
    public FileMetadata getFileForDownload(String fileId) {
        FileMetadata metadata = fileStore.get(fileId);
        if (metadata == null) {
            throw new RoomNotFoundException("File not found");
        }

        // Check room
        ChatRoom room = roomStore.get(metadata.getRoomCode());
        if (room == null || room.isExpired()) {
            throw new RoomNotFoundException("Room has been closed. File is no longer available.");
        }

        // Check expiry
        if (metadata.isExpired()) {
            throw new RoomNotFoundException("File has expired");
        }

        return metadata;
    }
    
    /**
     * Dispatch new file message event via WebSocket.
     */
    private void dispatchFileMessageEvent(String roomCode, ChatMessage message, String senderName, String downloadUrl) {
        try {
            // Create MessageResponseDto for the file message
            MessageResponseDto responseDto = MessageResponseDto.builder()
                    .messageId(message.getMessageId())
                    .roomCode(roomCode)
                    .senderId(message.getUserId())
                    .senderName(senderName)
                    .content(message.getContent())
                    .type(message.getType().name())
                    .fileId(message.getFileId())
                    .downloadUrl(downloadUrl)
                    .timestamp(message.getTimestamp())
                    .expiryTime(message.getExpiryTime())
                    .edited(false)
                    .deleted(false)
                    .lastModified(message.getLastModified())
                    .build();
            
            RealtimeEvent event = BaseRealtimeEvent.messageEvent(roomCode, 
                WebSocketEventDto.messageEvent(roomCode, responseDto));
            realtimeDispatcher.dispatch(event, null);
            log.debug("Dispatched file message event for room {} via WebSocket", roomCode);
        } catch (Exception e) {
            log.error("Failed to dispatch file message event: {}", e.getMessage(), e);
        }
    }
}


