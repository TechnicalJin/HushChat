package com.code.HushChat.service;

import com.code.HushChat.config.AppConfig;
import com.code.HushChat.dto.FileUploadResponseDto;
import com.code.HushChat.exception.RoomNotFoundException;
import com.code.HushChat.exception.UnauthorizedException;
import com.code.HushChat.model.ChatMessage;
import com.code.HushChat.model.ChatRoom;
import com.code.HushChat.model.FileMetadata;
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

    /**
     * Upload a file for a room and return metadata + download URL.
     */
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

        // Ensure upload directory exists
        String uploadDir = appConfig.getFile().getUploadDir();
        FileUtil.createDirectoryIfNotExists(uploadDir);

        // Generate stored filename and path
        String fileId = UUID.randomUUID().toString();
        String safeOriginal = FileUtil.sanitizeFilename(originalFilename);
        String storedFilename = fileId + "_" + safeOriginal;

        Path targetPath = Paths.get(uploadDir).resolve(storedFilename).toAbsolutePath();
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

        String downloadUrl = baseDownloadUrl + "/api/files/" + fileId + "/download";

        log.info("File uploaded - id: {}, room: {}, user: {}, name: {}, size: {} bytes",
                fileId, roomCode, userId, originalFilename, sizeBytes);

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
}


