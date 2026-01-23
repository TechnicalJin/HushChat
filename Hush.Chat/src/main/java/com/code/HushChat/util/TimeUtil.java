package com.code.HushChat.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class TimeUtil {
    
    private static final DateTimeFormatter FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    public static LocalDateTime now() {
        return LocalDateTime.now();
    }
    
    public static LocalDateTime addMinutes(int minutes) {
        return LocalDateTime.now().plusMinutes(minutes);
    }
    
    public static LocalDateTime addHours(int hours) {
        return LocalDateTime.now().plusHours(hours);
    }
    
    public static String format(LocalDateTime dateTime) {
        return dateTime.format(FORMATTER);
    }
    
    public static boolean isExpired(LocalDateTime expiryTime) {
        return LocalDateTime.now().isAfter(expiryTime);
    }
    
    public static boolean isBefore(LocalDateTime time1, LocalDateTime time2) {
        return time1.isBefore(time2);
    }
}