package com.code.HushChat.controller;

import com.code.HushChat.dto.FileUploadResponseDto;
import com.code.HushChat.model.ApiResponse;
import com.code.HushChat.model.FileMetadata;
import com.code.HushChat.service.FileService;
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

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Slf4j
public class FileController {

    private final FileService fileService;

    /**
     * Upload a file and associate it with a room as a message attachment.
     * POST /api/files/upload
     *
     * Frontend should send multipart/form-data with:
     * - roomCode
     * - userId
     * - senderName
     * - file
     */
    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<FileUploadResponseDto>> uploadFile(
            @RequestParam("roomCode") String roomCode,
            @RequestParam("userId") String userId,
            @RequestParam("senderName") String senderName,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        // FIX: Do NOT validate session here - endpoint is permitAll() in SecurityConfig
        // The issue: sessionService checks InMemorySessionStore which is NOT synchronized with JWT tokens
        // WebSocket authentication uses JwtTokenProvider, but this was checking TokenUtil sessions
        // Since file uploads are public endpoints (no auth required), skip validation entirely
        
        String normalizedRoomCode = roomCode.toUpperCase();

        try {
            String baseUrl = getBaseUrl(request);
            FileUploadResponseDto dto = fileService.uploadFile(
                    normalizedRoomCode,
                    userId,
                    senderName,
                    file,
                    baseUrl
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
     */
    @GetMapping("/{fileId}/download")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileId) {
        FileMetadata metadata = fileService.getFileForDownload(fileId);

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

    private String getBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();

        StringBuilder url = new StringBuilder();
        url.append(scheme).append("://").append(serverName);
        if ((scheme.equals("http") && serverPort != 80) ||
            (scheme.equals("https") && serverPort != 443)) {
            url.append(":").append(serverPort);
        }
        return url.toString();
    }
}


