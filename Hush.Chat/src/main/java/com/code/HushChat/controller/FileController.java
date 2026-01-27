package com.code.HushChat.controller;

import com.code.HushChat.dto.FileUploadResponseDto;
import com.code.HushChat.dto.MessageResponseDto;
import com.code.HushChat.model.ApiResponse;
import com.code.HushChat.model.ChatRoom;
import com.code.HushChat.model.FileMetadata;
import com.code.HushChat.service.FileService;
import com.code.HushChat.service.MessageService;
import com.code.HushChat.storage.InMemoryRoomStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for file upload, download, and management.
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Slf4j
public class FileController {

    private final FileService fileService;
    private final MessageService messageService;
    private final InMemoryRoomStore roomStore;

    /**
     * Upload one or more files to a room.
     * POST /api/files/upload
     *
     * @param files    the files to upload
     * @param roomCode the room code
     * @param userId   the user uploading the files
     * @return list of uploaded file metadata
     */
    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<List<FileUploadResponseDto>>> uploadFiles(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam("roomCode") String roomCode,
            @RequestParam("userId") String userId) {

        roomCode = roomCode.toUpperCase();

        log.info("File upload request - Room: {}, User: {}, Files: {}", roomCode, userId, files.length);

        // Validate at least one file
        if (files == null || files.length == 0) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("No files provided")
            );
        }

        // Get room for user name lookup
        ChatRoom room = roomStore.get(roomCode);
        String uploaderName = "Unknown";
        if (room != null) {
            String name = room.getUserIdToName().get(userId);
            if (name != null) {
                uploaderName = name;
            }
        }

        // Upload files
        List<FileMetadata> uploadedFiles = fileService.uploadFiles(files, roomCode, userId);

        if (uploadedFiles.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Failed to upload any files")
            );
        }

        // Create file messages for each uploaded file
        for (FileMetadata metadata : uploadedFiles) {
            try {
                messageService.sendFileMessage(
                    roomCode,
                    userId,
                    metadata.getFileId(),
                    metadata.getOriginalFilename(),
                    metadata.getContentType(),
                    metadata.getFileSize()
                );
            } catch (Exception e) {
                log.warn("Failed to create message for file: {} - {}", metadata.getFileId(), e.getMessage());
            }
        }

        // Convert to response DTOs
        List<FileUploadResponseDto> responses = new ArrayList<>();
        for (FileMetadata metadata : uploadedFiles) {
            responses.add(toResponseDto(metadata, uploaderName));
        }

        return ResponseEntity.ok(
                ApiResponse.success("Files uploaded successfully", responses)
        );
    }

    /**
     * Download a file.
     * GET /api/files/download/{fileId}
     *
     * @param fileId the file ID
     * @param userId the user requesting the file
     * @return the file as a stream
     */
    @GetMapping("/download/{fileId}")
    public ResponseEntity<?> downloadFile(
            @PathVariable String fileId,
            @RequestParam String userId) {

        log.debug("File download request - FileId: {}, User: {}", fileId, userId);

        try {
            // Get file metadata
            FileMetadata metadata = fileService.getFileMetadata(fileId);
            if (metadata == null) {
                return ResponseEntity.notFound().build();
            }

            // Get file resource
            Resource resource = fileService.downloadFile(fileId, userId);

            // Determine content type
            String contentType = metadata.getContentType();
            if (contentType == null || contentType.isEmpty()) {
                contentType = "application/octet-stream";
            }

            // Encode filename for Content-Disposition header
            String encodedFilename = URLEncoder.encode(metadata.getOriginalFilename(), StandardCharsets.UTF_8)
                    .replace("+", "%20");

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + metadata.getOriginalFilename() + "\"; " +
                                    "filename*=UTF-8''" + encodedFilename)
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(metadata.getFileSize()))
                    .body(resource);

        } catch (Exception e) {
            log.error("File download failed - FileId: {}, Error: {}", fileId, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Preview a file (inline display for images).
     * GET /api/files/preview/{fileId}
     *
     * @param fileId the file ID
     * @param userId the user requesting the file
     * @return the file for inline display
     */
    @GetMapping("/preview/{fileId}")
    public ResponseEntity<?> previewFile(
            @PathVariable String fileId,
            @RequestParam String userId) {

        log.debug("File preview request - FileId: {}, User: {}", fileId, userId);

        try {
            // Get file metadata
            FileMetadata metadata = fileService.getFileMetadata(fileId);
            if (metadata == null) {
                return ResponseEntity.notFound().build();
            }

            // Get file resource
            Resource resource = fileService.downloadFile(fileId, userId);

            // Determine content type
            String contentType = metadata.getContentType();
            if (contentType == null || contentType.isEmpty()) {
                contentType = "application/octet-stream";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(metadata.getFileSize()))
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                    .body(resource);

        } catch (Exception e) {
            log.error("File preview failed - FileId: {}, Error: {}", fileId, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Delete a file.
     * DELETE /api/files/{fileId}
     *
     * @param fileId the file ID
     * @param userId the user requesting deletion
     * @return success/error response
     */
    @DeleteMapping("/{fileId}")
    public ResponseEntity<ApiResponse<Void>> deleteFile(
            @PathVariable String fileId,
            @RequestParam String userId) {

        log.info("File delete request - FileId: {}, User: {}", fileId, userId);

        try {
            fileService.deleteFile(fileId, userId);
            return ResponseEntity.ok(
                    ApiResponse.success("File deleted successfully", null)
            );
        } catch (Exception e) {
            log.error("File delete failed - FileId: {}, Error: {}", fileId, e.getMessage());
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Failed to delete file: " + e.getMessage())
            );
        }
    }

    /**
     * Get file metadata.
     * GET /api/files/{fileId}/info
     *
     * @param fileId the file ID
     * @param userId the user requesting info
     * @return file metadata
     */
    @GetMapping("/{fileId}/info")
    public ResponseEntity<ApiResponse<FileUploadResponseDto>> getFileInfo(
            @PathVariable String fileId,
            @RequestParam String userId) {

        FileMetadata metadata = fileService.getFileMetadata(fileId);
        if (metadata == null || metadata.isExpired()) {
            return ResponseEntity.notFound().build();
        }

        // Get uploader name
        ChatRoom room = roomStore.get(metadata.getRoomCode());
        String uploaderName = "Unknown";
        if (room != null) {
            String name = room.getUserIdToName().get(metadata.getUserId());
            if (name != null) {
                uploaderName = name;
            }
        }

        return ResponseEntity.ok(
                ApiResponse.success("File info retrieved", toResponseDto(metadata, uploaderName))
        );
    }

    /**
     * Get upload configuration for frontend.
     * GET /api/files/config
     *
     * @return upload configuration
     */
    @GetMapping("/config")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUploadConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("maxFileSizeBytes", fileService.getMaxFileSizeBytes());
        config.put("maxFileSizeMB", fileService.getMaxFileSizeBytes() / (1024 * 1024));
        config.put("allowedExtensions", fileService.getAllowedExtensions());

        return ResponseEntity.ok(
                ApiResponse.success("Upload config retrieved", config)
        );
    }

    /**
     * Convert FileMetadata to FileUploadResponseDto.
     */
    private FileUploadResponseDto toResponseDto(FileMetadata metadata, String uploaderName) {
        return FileUploadResponseDto.builder()
                .fileId(metadata.getFileId())
                .roomCode(metadata.getRoomCode())
                .uploaderId(metadata.getUserId())
                .uploaderName(uploaderName)
                .originalFilename(metadata.getOriginalFilename())
                .contentType(metadata.getContentType())
                .fileSize(metadata.getFileSize())
                .downloadUrl("/api/files/download/" + metadata.getFileId())
                .uploadTime(metadata.getUploadTime())
                .expiryTime(metadata.getExpiryTime())
                .build();
    }
}
