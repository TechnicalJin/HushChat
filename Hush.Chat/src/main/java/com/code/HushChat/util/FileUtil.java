package com.code.HushChat.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class FileUtil {
    
    public static boolean isValidFileType(String filename, String allowedTypes) {
        if (filename == null || allowedTypes == null) {
            return false;
        }
        
        String extension = getFileExtension(filename);
        List<String> allowed = Arrays.asList(allowedTypes.split(","));
        
        return allowed.stream()
            .anyMatch(type -> type.trim().equalsIgnoreCase(extension));
    }
    
    public static String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }
    
    public static void createDirectoryIfNotExists(String path) throws IOException {
        Path dirPath = Paths.get(path);
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
        }
    }
    
    public static boolean deleteFile(String filePath) {
        try {
            File file = new File(filePath);
            return file.delete();
        } catch (Exception e) {
            System.err.println("Failed to delete file: " + filePath);
            return false;
        }
    }
    
    public static void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }
    
    /**
     * SECURITY FIX #9B: Sanitize filename to prevent path traversal, null bytes,
     * and other injection attacks.
     *
     * <p>Removes path separators, null bytes, and special characters. Limits
     * filename length. Returns a safe fallback for null/blank inputs.</p>
     *
     * @param filename the original filename from the upload
     * @return a sanitized filename safe for filesystem storage
     */
    public static String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "unnamed";
        }
        // SECURITY FIX #9B: Strip directory path components (path traversal)
        String safe = filename;
        // Remove any path separators (Unix and Windows)
        safe = safe.replace('\\', '_');
        safe = safe.replace('/', '_');
        // Remove null bytes
        safe = safe.replace("\0", "");
        // Remove leading dots (hidden files / relative path tricks)
        safe = safe.replaceAll("^\\.+", "");
        // Remove potentially dangerous characters; keep alphanumeric, dots, hyphens, underscores
        safe = safe.replaceAll("[^a-zA-Z0-9._\\-]", "_");
        // Collapse multiple consecutive underscores/dots
        safe = safe.replaceAll("[_.]{2,}", "_");
        // Trim length (255 is a safe filesystem limit)
        if (safe.length() > 200) {
            safe = safe.substring(0, 200);
        }
        // Final fallback
        if (safe.isBlank()) {
            return "unnamed";
        }
        return safe;
    }
    
    public static long getFileSizeInMB(long bytes) {
        return bytes / (1024 * 1024);
    }

    /**
     * SECURITY FIX #9B: Known dangerous executable extensions that should
     * never be allowed for upload regardless of MIME type.
     */
    private static final List<String> DANGEROUS_EXTENSIONS = List.of(
        "exe", "bat", "cmd", "com", "msi", "scr", "pif", "vbs", "vbe", "js",
        "jse", "ws", "wsc", "wsf", "ps1", "psm1", "psd1", "psc1", "reg",
        "dll", "sys", "cpl", "hta", "inf", "msp", "mst", "gadget", "application"
    );

    /**
     * SECURITY FIX #9B: Check whether a filename has a known dangerous extension.
     *
     * @param filename the original filename
     * @return true if the extension is considered dangerous
     */
    public static boolean isDangerousExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return false;
        }
        String ext = getFileExtension(filename);
        return DANGEROUS_EXTENSIONS.contains(ext);
    }

    /**
     * SECURITY FIX #9B: Map file extensions to expected MIME types for
     * content-type validation.
     */
    private static final Map<String, String> EXTENSION_TO_MIME = Map.ofEntries(
        Map.entry("jpg",  "image/jpeg"),
        Map.entry("jpeg", "image/jpeg"),
        Map.entry("png",  "image/png"),
        Map.entry("gif",  "image/gif"),
        Map.entry("pdf",  "application/pdf"),
        Map.entry("txt",  "text/plain"),
        Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    );

    /**
     * SECURITY FIX #9B: Validate that the declared MIME type is consistent with
     * the file extension.
     *
     * @param filename  the original filename
     * @param mimeType  the declared MIME type from the multipart request
     * @return true if the MIME type matches the extension, or if the extension
     *         is not in the known mapping (permissive for unknown types)
     */
    public static boolean isMimeTypeConsistent(String filename, String mimeType) {
        if (filename == null || mimeType == null) {
            return false;
        }
        String ext = getFileExtension(filename);
        if (ext.isEmpty()) {
            return false;
        }
        String expectedMime = EXTENSION_TO_MIME.get(ext);
        if (expectedMime == null) {
            // Extension not in our known map — allow if MIME is not empty
            return !mimeType.isBlank();
        }
        return expectedMime.equalsIgnoreCase(mimeType.trim());
    }
}