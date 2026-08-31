package com.code.HushChat.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileServiceUrlGenerationTest {

    @Test
    void buildRelativeFileDownloadUrl_shouldReturnRelativePath() {
        String fileId = "abc123";

        String result = FileService.buildRelativeFileDownloadUrl(fileId);

        assertEquals("/api/files/abc123/download", result);
    }
}
