package com.code.HushChat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpResponseDto {
    private String message;
    private int expiryMinutes;
    private int retriesLeft;
    private LocalDateTime expiryTime;
}