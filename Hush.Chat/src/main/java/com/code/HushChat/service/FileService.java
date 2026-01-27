package com.code.HushChat.service;

import com.code.HushChat.config.AppConfig;
import com.code.HushChat.exception.RoomNotFoundException;
import com.code.HushChat.exception.UnauthorizedException;
import com.code.HushChat.model.ChatRoom;
import com.code.HushChat.model.FileMetadata;
import com.code.HushChat.storage.InMemoryFileStore;
import com.code.HushChat.storage.InMemoryRoomStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for handling file uploads, downloads, and management.
 * Files are stored in ./uploads/{roomCode}/ directory.
 * Each file has TTL matching message TTL (10 minutes by default).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileService {

    private final InMemoryFileStore fileStore;
    private final InMemoryRoomStore roomStore;
    private final AppConfig appConfig;

    private Path uploadRootPath;
    private Set<String> allowedExtensions;

    @PostConstruct
    public void init() {
        // Initialize upload directory
        uploadRootPath = Paths.get(appConfig.getFile().getUploadDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadRootPath);
            log.info("File upload directory initialized: {}", uploadRootPath);
        } catch (IOException e) {
            log.error("Failed to create upload directory: {}", e.getMessage());
            throw new RuntimeException("Could not create upload directory", e);
        }

        // Parse allowed extensions
        String allowedTypesStr = appConfig.getFile().getAllowedTypes();
        allowedExtensions = new HashSet<>();
        if (allowedTypesStr != null && !allowedTypesStr.isEmpty()) {
            for (String ext : allowedTypesStr.split(",")) {
                allowedExtensions.add(ext.trim().toLowerCase());
            }
        }
        log.info("Allowed file extensions: {}", allowedExtensions);
    }

    /**
     * Upload multiple files to a room.
     *
     * @param files    the files to upload
     * @param roomCode the room code
     * @param userId   the user uploading the files
     * @return list of file metadata for uploaded files
     */
    public List<FileMetadata> uploadFiles(MultipartFile[] files, String roomCode, String userId) {
        // Validate room exists
        ChatRoom room = roomStore.get(roomCode);
        if (room == null || room.isExpired()) {
            throw new RoomNotFoundException("Room not found or expired: " + roomCode);
        }

        // Validate user is in room
        if (!room.getActiveUserIds().contains(userId)) {
            throw new UnauthorizedException("User is not a member of this room");
        }

        List<FileMetadata> uploadedFiles = new ArrayList<>();

        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                continue;
            }

            try {
                FileMetadata metadata = uploadSingleFile(file, roomCode, userId);
                uploadedFiles.add(metadata);
            } catch (Exception e) {
                log.error("Failed to upload file: {} - {}", file.getOriginalFilename(), e.getMessage());
                // Continue with other files
            }
        }

        return uploadedFiles;
    }

    /**
     * Upload a single file.
     */
    private FileMetadata uploadSingleFile(MultipartFile file, String roomCode, String userId) throws IOException {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            originalFilename = "unnamed_file";
        }

        // Validate file extension
        String extension = getFileExtension(originalFilename);
        if (!isAllowedExtension(extension)) {
            throw new IllegalArgumentException("File type not allowed: " + extension);
        }

        // Validate file size
        long maxSizeBytes = (long) appConfig.getFile().getMaxSizeMb() * 1024 * 1024;
        if (file.getSize() > maxSizeBytes) {
            throw new IllegalArgumentException("File size exceeds maximum allowed: " + appConfig.getFile().getMaxSizeMb() + "MB");
        }

        // Generate unique file ID
        String fileId = UUID.randomUUID().toString();

        // Create room directory if not exists
        Path roomDir = uploadRootPath.resolve(roomCode);
        Files.createDirectories(roomDir);

        // Generate stored filename (unique)
        String storedFilename = fileId + "_" + sanitizeFilename(originalFilename);
        Path targetPath = roomDir.resolve(storedFilename);

        // Copy file to storage
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        // Create metadata with TTL
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiryTime = now.plusMinutes(appConfig.getMessage().getTtlMinutes());

        FileMetadata metadata = FileMetadata.builder()
                .fileId(fileId)
                .roomCode(roomCode)
                .userId(userId)
                .originalFilename(originalFilename)
                .storedFilename(storedFilename)
                .contentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                .fileSize(file.getSize())
                .filePath(targetPath.toString())
                .uploadTime(now)
                .expiryTime(expiryTime)
                .build();

        // Save metadata to store
        fileStore.save(fileId, metadata);

        log.info("File uploaded - ID: {}, Room: {}, Name: {}, Size: {} bytes",
                fileId, roomCode, originalFilename, file.getSize());

        return metadata;
    }

    /**
     * Get file metadata by ID.
     */
    public FileMetadata getFileMetadata(String fileId) {
        return fileStore.get(fileId);
    }

    /**
     * Download a file as a Resource.
     *
     * @param fileId the file ID
     * @param userId the user requesting the file
     * @return the file as a Resource
     */
    public Resource downloadFile(String fileId, String userId) {
        FileMetadata metadata = fileStore.get(fileId);

        if (metadata == null) {
            throw new RoomNotFoundException("File not found: " + fileId);
        }

        // Check if file is expired
        if (metadata.isExpired()) {
            // Clean up expired file
            deleteFile(fileId, metadata.getUserId());
            throw new RoomNotFoundException("File has expired: " + fileId);
        }

        // Validate room still exists
        ChatRoom room = roomStore.get(metadata.getRoomCode());
        if (room == null || room.isExpired()) {
            throw new RoomNotFoundException("Room no longer exists");
        }

        // Validate user is in room
        if (!room.getActiveUserIds().contains(userId)) {
            throw new UnauthorizedException("User is not a member of this room");
        }

        try {
            Path filePath = Paths.get(metadata.getFilePath());
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new RoomNotFoundException("File not found on disk: " + fileId);
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("Error reading file: " + fileId, e);
        }
    }

    /**
     * Delete a file.
     *
     * @param fileId the file ID
     * @param userId the user requesting deletion
     */
    public void deleteFile(String fileId, String userId) {
        FileMetadata metadata = fileStore.get(fileId);

        if (metadata == null) {
            return; // File doesn't exist, nothing to do
        }

        // Validate user owns the file (or file is expired)
        if (!metadata.isExpired() && !metadata.getUserId().equals(userId)) {
            throw new UnauthorizedException("You can only delete your own files");
        }

        // Delete from disk
        try {
            Path filePath = Paths.get(metadata.getFilePath());
            Files.deleteIfExists(filePath);
            log.debug("Deleted file from disk: {}", metadata.getFilePath());
        } catch (IOException e) {
            log.warn("Failed to delete file from disk: {} - {}", metadata.getFilePath(), e.getMessage());
        }

        // Remove from store
        fileStore.delete(fileId);

        log.info("File deleted - ID: {}, Room: {}", fileId, metadata.getRoomCode());
    }

    /**
     * Delete all files for a room.
     * Called when room is destroyed.
     */
    public void deleteRoomFiles(String roomCode) {
        Set<String> fileIds = fileStore.getRoomFiles(roomCode);

        for (String fileId : fileIds) {
            FileMetadata metadata = fileStore.get(fileId);
            if (metadata != null) {
                try {
                    Path filePath = Paths.get(metadata.getFilePath());
                    Files.deleteIfExists(filePath);
                } catch (IOException e) {
                    log.warn("Failed to delete room file: {} - {}", metadata.getFilePath(), e.getMessage());
                }
            }
        }

        // Remove from store
        fileStore.deleteRoomFiles(roomCode);

        // Try to delete room directory
        try {
            Path roomDir = uploadRootPath.resolve(roomCode);
            if (Files.exists(roomDir)) {
                Files.delete(roomDir);
            }
        } catch (IOException e) {
            log.debug("Could not delete room directory (may not be empty): {}", roomCode);
        }

        log.info("Deleted all files for room: {}", roomCode);
    }

    /**
     * Get all files for a room.
     */
    public List<FileMetadata> getRoomFiles(String roomCode) {
        Set<String> fileIds = fileStore.getRoomFiles(roomCode);
        List<FileMetadata> files = new ArrayList<>();

        for (String fileId : fileIds) {
            FileMetadata metadata = fileStore.get(fileId);
            if (metadata != null && !metadata.isExpired()) {
                files.add(metadata);
            }
        }

        return files;
    }

    /**
     * Check if file extension is allowed.
     */
    public boolean isAllowedExtension(String extension) {
        if (extension == null || extension.isEmpty()) {
            return false;
        }
        // If no extensions configured, allow all
        if (allowedExtensions.isEmpty()) {
            return true;
        }
        return allowedExtensions.contains(extension.toLowerCase());
    }

    /**
     * Get file extension from filename.
     */
    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        int lastDot = filename.lastIndexOf('.');
        if (lastDot < 0) {
            return "";
        }
        return filename.substring(lastDot + 1).toLowerCase();
    }

    /**
     * Sanitize filename to remove dangerous characters.
     */
    private String sanitizeFilename(String filename) {
        if (filename == null) {
            return "file";
        }
        // Remove path separators and other dangerous chars
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * Get allowed extensions for frontend validation.
     */
    public Set<String> getAllowedExtensions() {
        return Collections.unmodifiableSet(allowedExtensions);
    }

    /**
     * Get max file size in bytes.
     */
    public long getMaxFileSizeBytes() {
        return (long) appConfig.getFile().getMaxSizeMb() * 1024 * 1024;
    }
}
