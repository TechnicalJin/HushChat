package com.code.HushChat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateRoomDto {
    
    @NotBlank(message = "Room name is required")
    @Size(min = 3, max = 50, message = "Room name must be between 3 and 50 characters")
    private String roomName;
    
    @NotBlank(message = "User name is required")
    @Size(min = 2, max = 30, message = "User name must be between 2 and 30 characters")
    private String userName;
    
    private Integer maxUsers; // Optional, will use default from config if not provided
}
