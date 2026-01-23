package com.code.HushChat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OtpRequestDto {
    
    @NotBlank(message = "Device ID is required")
    @Size(min = 10, max = 100, message = "Device ID must be between 10 and 100 characters")
    private String deviceId;
}