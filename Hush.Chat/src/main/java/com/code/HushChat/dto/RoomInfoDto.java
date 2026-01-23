package com.code.HushChat.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@Builder
public class RoomInfoDto {
    private String roomCode;
    private String roomName;
    private int currentUsers;
    private int maxUsers;
    private Set<String> userNames;
    private LocalDateTime createdAt;
    private LocalDateTime lastActivity;
}
