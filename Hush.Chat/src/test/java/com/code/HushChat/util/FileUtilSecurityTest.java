package com.code.HushChat.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SECURITY FIX #9B regression tests for the hardened FileUtil helpers:
 * filename sanitization, dangerous-extension blocking, and MIME type
 * consistency validation.
 */
class FileUtilSecurityTest {

    // ===== sanitizeFilename =====

    @Test
    void sanitizeFilename_removesUnixPathTraversal() {
        // '/' becomes '_' and leading separators are retained as '_' (safe)
        assertEquals("_var_secret.txt", FileUtil.sanitizeFilename("/var/secret.txt"));
    }

    @Test
    void sanitizeFilename_removesWindowsPathTraversal() {
        String result = FileUtil.sanitizeFilename("..\\..\\windows\\secret.txt");
        assertFalse(result.contains(".."), "sanitized name must not retain traversal dots");
        assertFalse(result.contains("\\"), "sanitized name must not retain backslashes");
        assertEquals("_windows_secret.txt", result);
    }

    @Test
    void sanitizeFilename_removesNullBytes() {
        assertEquals("bad.txt", FileUtil.sanitizeFilename("bad\u0000.txt"));
    }

    @Test
    void sanitizeFilename_removesLeadingDots() {
        assertEquals("evil.txt", FileUtil.sanitizeFilename("...evil.txt"));
    }

    @Test
    void sanitizeFilename_stripsQueryAndControlCharacters() {
        // '?' and '&' become '_', and the adjacent '_' + '.' collapse to '_'
        String result = FileUtil.sanitizeFilename("resume?pdf&.txt");
        assertFalse(result.contains("?"), "sanitized name must not contain '?'");
        assertFalse(result.contains("&"), "sanitized name must not contain '&'");
        // result == "resume_pdf_txt"
        assertEquals("resume_pdf_txt", result);
    }

    @Test
    void sanitizeFilename_nullAndBlankReturnUnnamed() {
        assertEquals("unnamed", FileUtil.sanitizeFilename(null));
        assertEquals("unnamed", FileUtil.sanitizeFilename("   "));
    }

    @Test
    void sanitizeFilename_truncatesVeryLongNames() {
        String veryLong = "a".repeat(500) + ".txt";
        String result = FileUtil.sanitizeFilename(veryLong);
        assertTrue(result.length() <= 200, "sanitized filename must be capped at 200 chars");
    }

    @Test
    void sanitizeFilename_keepsNormalNamesUntouched() {
        assertEquals("report_final_v2.xlsx", FileUtil.sanitizeFilename("report_final_v2.xlsx"));
    }

    // ===== isDangerousExtension =====

    @Test
    void dangerousExtension_rejectsExecutables() {
        assertTrue(FileUtil.isDangerousExtension("malware.exe"));
        assertTrue(FileUtil.isDangerousExtension("script.bat"));
        assertTrue(FileUtil.isDangerousExtension("payload.vbs"));
        assertTrue(FileUtil.isDangerousExtension("runner.ps1"));
        assertTrue(FileUtil.isDangerousExtension("page.jse"));
    }

    @Test
    void dangerousExtension_rejectsWindowsArtifacts() {
        assertTrue(FileUtil.isDangerousExtension("setup.msi"));
        assertTrue(FileUtil.isDangerousExtension("driver.sys"));
        assertTrue(FileUtil.isDangerousExtension("lib.dll"));
        assertTrue(FileUtil.isDangerousExtension("page.hta"));
    }

    @Test
    void dangerousExtension_allowsBenignTypes() {
        assertFalse(FileUtil.isDangerousExtension("photo.png"));
        assertFalse(FileUtil.isDangerousExtension("doc.pdf"));
        assertFalse(FileUtil.isDangerousExtension("hello.txt"));
        assertFalse(FileUtil.isDangerousExtension("notes.md"));
    }

    @Test
    void dangerousExtension_isCaseInsensitive() {
        assertTrue(FileUtil.isDangerousExtension("SCHEME.EXE"));
        assertTrue(FileUtil.isDangerousExtension("upload.ExE"));
    }

    @Test
    void dangerousExtension_nullOrExtensionlessFilenameIsSafe() {
        assertFalse(FileUtil.isDangerousExtension(null));
        assertFalse(FileUtil.isDangerousExtension("noextension"));
        assertFalse(FileUtil.isDangerousExtension("readme"));
    }

    // ===== isMimeTypeConsistent =====

    @Test
    void mimeConsistency_matchesKnownExtension() {
        assertTrue(FileUtil.isMimeTypeConsistent("photo.png", "image/png"));
        assertTrue(FileUtil.isMimeTypeConsistent("doc.pdf", "application/pdf"));
        assertTrue(FileUtil.isMimeTypeConsistent("note.txt", "text/plain"));
        assertTrue(FileUtil.isMimeTypeConsistent("pic.JPG", "image/jpeg"));
    }

    @Test
    void mimeConsistency_rejectsMismatch_forKnownExtension() {
        assertFalse(FileUtil.isMimeTypeConsistent("photo.png", "image/jpeg"));
        assertFalse(FileUtil.isMimeTypeConsistent("doc.pdf", "text/html"));
        assertFalse(FileUtil.isMimeTypeConsistent("note.txt", "application/pdf"));
    }

    @Test
    void mimeConsistency_allowsUnknownExtensionWithNonBlankMime() {
        assertTrue(FileUtil.isMimeTypeConsistent("archive.7z", "application/x-7z-compressed"));
        assertTrue(FileUtil.isMimeTypeConsistent("data.bin", "application/octet-stream"));
    }

    @Test
    void mimeConsistency_nullInputsRejected() {
        assertFalse(FileUtil.isMimeTypeConsistent(null, "image/png"));
        assertFalse(FileUtil.isMimeTypeConsistent("photo.png", null));
        assertFalse(FileUtil.isMimeTypeConsistent("", "image/png"));
        assertFalse(FileUtil.isMimeTypeConsistent("noextension", "text/plain"));
    }
}