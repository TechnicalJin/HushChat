package com.code.HushChat.controller;

import com.code.HushChat.dto.FileUploadResponseDto;
import com.code.HushChat.model.ApiResponse;
import com.code.HushChat.model.FileMetadata;
import com.code.HushChat.service.FileService;
import com.code.HushChat.service.RoomService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Slf4j
public class FileController {

    private final FileService fileService;
    private final RoomService roomService;

    /**
     * Upload a file and associate it with a room as a message attachment.
     * POST /api/files/upload
     *
     * Identity (userId and senderName) is derived from the authenticated
     * JWT principal and room membership - never from client-supplied fields.
     *
     * Frontend should send multipart/form-data with:
     * - roomCode
     * - file
     */
    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<FileUploadResponseDto>> uploadFile(
            @RequestParam("roomCode") String roomCode,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request,
            Principal principal
    ) {
        String userId = principal.getName();
        String normalizedRoomCode = roomCode.toUpperCase();

        // Resolve senderName from the room's userId -> name membership map.
        String senderName = "Unknown";
        try {
            Map<String, String> members = roomService.getMembersOfRoom(normalizedRoomCode);
            senderName = members.getOrDefault(userId, "Unknown");
        } catch (Exception e) {
            log.warn("Unable to resolve sender name for user {} in room {}: {}",
                userId, normalizedRoomCode, e.getMessage());
        }

        try {
            FileUploadResponseDto dto = fileService.uploadFile(
                    normalizedRoomCode,
                    userId,
                    senderName,
                    file,
                    null
            );

            return ResponseEntity.ok(
                    ApiResponse.success("File uploaded successfully", dto)
            );
        } catch (Exception e) {
            log.warn("File upload failed for room {} user {}: {}", normalizedRoomCode, userId, e.getMessage());
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(e.getMessage())
            );
        }
    }

    /**
     * Download a file by id.
     * GET /api/files/{fileId}/download
     * Requires the caller to be an authenticated member of the file's room.
     */
    @GetMapping("/{fileId}/download")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileId, Principal principal) {
        FileMetadata metadata = fileService.getFileForDownload(fileId);

        // Enforce room membership: only authenticated members of the file's room
        // may download it.
        String userId = principal.getName();
        if (!roomService.isUserInRoom(metadata.getRoomCode(), userId)) {
            return ResponseEntity.status(403).build();
        }

        FileSystemResource resource = new FileSystemResource(metadata.getFilePath());
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        String encodedFilename = URLEncoder.encode(
                metadata.getOriginalFilename(),
                StandardCharsets.UTF_8
        ).replaceAll("\\+", "%20");

        String contentType = metadata.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + encodedFilename + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(metadata.getFileSize())
                .body(resource);
    }

}


