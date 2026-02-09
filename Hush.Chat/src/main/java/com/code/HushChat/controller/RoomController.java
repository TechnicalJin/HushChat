package com.code.HushChat.controller;

import com.code.HushChat.dto.CreateRoomDto;
import com.code.HushChat.dto.JoinRoomDto;
import com.code.HushChat.dto.LeaveRoomDto;
import com.code.HushChat.dto.RoomInfoDto;
import com.code.HushChat.model.ApiResponse;
import com.code.HushChat.model.ChatRoom;
import com.code.HushChat.security.JwtTokenProvider;
import com.code.HushChat.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
@Slf4j
public class RoomController {
    
    private final RoomService roomService;
    private final JwtTokenProvider jwtTokenProvider;
    
    /**
     * Create a new chat room
     */
    @PostMapping("/create")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createRoom(@Valid @RequestBody CreateRoomDto dto) {
        log.info("Creating room: {}", dto.getRoomName());
        
        ChatRoom room = roomService.createRoom(dto);
        
        // Generate JWT token for WebSocket authentication
        String token = jwtTokenProvider.generateToken(room.getCreatorId());
        
        Map<String, Object> response = new HashMap<>();
        response.put("roomCode", room.getRoomCode());
        response.put("roomName", room.getRoomName());
        response.put("userId", room.getCreatorId());
        response.put("userName", dto.getUserName());
        response.put("token", token);  // For WebSocket auth
        response.put("maxUsers", room.getMaxUsers());
        response.put("createdAt", room.getCreatedAt());
        
        return ResponseEntity.ok(
            ApiResponse.success("Room created successfully", response)
        );
    }
    
    /**
     * Join an existing chat room
     */
    @PostMapping("/join")
    public ResponseEntity<ApiResponse<Map<String, Object>>> joinRoom(@Valid @RequestBody JoinRoomDto dto) {
        log.info("Joining room: {}", dto.getRoomCode());
        
        ChatRoom room = roomService.joinRoom(dto);
        
        // Find the user ID that was just added (last one in the set)
        String userId = room.getActiveUserIds().stream()
            .filter(id -> room.getUserIdToName().get(id).equals(dto.getUserName()))
            .findFirst()
            .orElse(null);
        
        // Generate JWT token for WebSocket authentication
        String token = jwtTokenProvider.generateToken(userId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("roomCode", room.getRoomCode());
        response.put("roomName", room.getRoomName());
        response.put("userId", userId);
        response.put("userName", dto.getUserName());
        response.put("token", token);  // For WebSocket auth
        response.put("currentUsers", room.getActiveUserIds().size());
        response.put("maxUsers", room.getMaxUsers());
        
        return ResponseEntity.ok(
            ApiResponse.success("Joined room successfully", response)
        );
    }
                   
    /**
     * Leave a chat room
     */
    @PostMapping("/leave")
    public ResponseEntity<ApiResponse<Void>> leaveRoom(@Valid @RequestBody LeaveRoomDto dto) {
        log.info("Leaving room: {}", dto.getRoomCode());
        
        roomService.leaveRoom(dto.getRoomCode(), dto.getUserId());
        
        return ResponseEntity.ok(
            ApiResponse.success("Left room successfully", null)
        );
    }
    
    /**
     * Get room information
     */
    @GetMapping("/{roomCode}/info")
    public ResponseEntity<ApiResponse<RoomInfoDto>> getRoomInfo(@PathVariable String roomCode) {
        // Use debug level to avoid flooding logs (this endpoint is polled frequently)
        log.debug("Getting room info: {}", roomCode);
        
        RoomInfoDto roomInfo = roomService.getRoomInfo(roomCode.toUpperCase());
        
        return ResponseEntity.ok(
            ApiResponse.success("Room info retrieved successfully", roomInfo)
        );
    }
    
    /**
     * Check if room exists
     */
    @GetMapping("/{roomCode}/exists")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> checkRoomExists(@PathVariable String roomCode) {
        boolean exists = roomService.roomExists(roomCode.toUpperCase());
        
        Map<String, Boolean> response = new HashMap<>();
        response.put("exists", exists);
        
        return ResponseEntity.ok(
            ApiResponse.success(exists ? "Room exists" : "Room not found", response)
        );
    }
}
