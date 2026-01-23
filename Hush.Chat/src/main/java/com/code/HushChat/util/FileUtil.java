package com.code.HushChat.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

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
    
    public static String sanitizeFilename(String filename) {
        if (filename == null) {
            return "unnamed";
        }
        // Remove potentially dangerous characters
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
    
    public static long getFileSizeInMB(long bytes) {
        return bytes / (1024 * 1024);
    }
}