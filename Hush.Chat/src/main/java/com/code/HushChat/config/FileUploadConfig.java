package com.code.HushChat.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

import java.io.File;

@Configuration
@RequiredArgsConstructor
public class FileUploadConfig {
    
    private final AppConfig appConfig;
    
    @PostConstruct
    public void init() {
        // Create upload directory if it doesn't exist
        String uploadDir = appConfig.getFile().getUploadDir();
        File directory = new File(uploadDir);
        
        if (!directory.exists()) {
            boolean created = directory.mkdirs();
            if (created) {
                System.out.println("📁 Created upload directory: " + uploadDir);
            }
        } else {
            System.out.println("📁 Upload directory exists: " + uploadDir);
        }
        
        // Ensure directory is writable
        if (!directory.canWrite()) {
            System.err.println("⚠️ Warning: Upload directory is not writable!");
        }
    }
}
