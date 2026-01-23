package com.code.HushChat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LeaveRoomDto {
    
    @NotBlank(message = "Room code is required")
    private String roomCode;
    
    @NotBlank(message = "User ID is required")
    private String userId;
}
