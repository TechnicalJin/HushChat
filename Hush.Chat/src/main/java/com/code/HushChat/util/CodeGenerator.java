package com.code.HushChat.util;

import org.apache.commons.lang3.RandomStringUtils;

import java.security.SecureRandom;

public class CodeGenerator {
    
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String OTP_CHARS = "0123456789";
    private static final String ROOM_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    
    public static String generateOtp(int length) {
        return RandomStringUtils.random(length, 0, OTP_CHARS.length(), 
            false, false, OTP_CHARS.toCharArray(), RANDOM);
    }
    
    public static String generateRoomCode(int length) {
        return RandomStringUtils.random(length, 0, ROOM_CODE_CHARS.length(), 
            false, false, ROOM_CODE_CHARS.toCharArray(), RANDOM);
    }
    
    public static String generateSessionToken() {
        return RandomStringUtils.randomAlphanumeric(64);
    }
    
    public static String generateUserId() {
        return "user_" + RandomStringUtils.randomAlphanumeric(16);
    }
    
    public static String generateMessageId() {
        return "msg_" + RandomStringUtils.randomAlphanumeric(16);
    }
    
    public static String generateFileId() {
        return "file_" + RandomStringUtils.randomAlphanumeric(16);
    }
}
