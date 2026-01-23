package com.code.HushChat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class JoinRoomDto {
    
    @NotBlank(message = "Room code is required")
    @Pattern(regexp = "^[A-Z0-9]{5}$", message = "Room code must be 5 alphanumeric characters")
    private String roomCode;
    
    @NotBlank(message = "User name is required")
    @Size(min = 2, max = 30, message = "User name must be between 2 and 30 characters")
    private String userName;
}
