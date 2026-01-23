package com.code.HushChat.service;

import com.code.HushChat.config.AppConfig;
import com.code.HushChat.dto.CreateRoomDto;
import com.code.HushChat.dto.JoinRoomDto;
import com.code.HushChat.dto.RoomInfoDto;
import com.code.HushChat.exception.RoomNotFoundException;
import com.code.HushChat.exception.UnauthorizedException;
import com.code.HushChat.model.ChatRoom;
import com.code.HushChat.storage.InMemoryRoomStore;
import com.code.HushChat.util.CodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoomService {
    
    private final InMemoryRoomStore roomStore;
    private final AppConfig appConfig;
    
    /**
     * Create a new chat room
     */
    public ChatRoom createRoom(CreateRoomDto dto) {
        // Generate unique room code
        String roomCode;
        do {
            roomCode = CodeGenerator.generateRoomCode(appConfig.getRoom().getCodeLength());
        } while (roomStore.get(roomCode) != null);
        
        // Generate unique user ID for creator
        String creatorId = UUID.randomUUID().toString();
        
        // Determine max users
        int maxUsers = dto.getMaxUsers() != null && dto.getMaxUsers() > 0 
            ? dto.getMaxUsers() 
            : appConfig.getRoom().getMaxUsers();
        
        // Create room
        ChatRoom room = new ChatRoom(
            roomCode,
            dto.getRoomName(),
            creatorId,
            dto.getUserName(),
            maxUsers,
            appConfig.getRoom().getInactivityTimeoutMinutes()
        );
        
        roomStore.save(roomCode, room);
        
        log.info("Room created - Code: {}, Name: {}, Creator: {}", 
            roomCode, dto.getRoomName(), dto.getUserName());
        
        return room;
    }
    
    /**
     * Join an existing chat room
     */
    public ChatRoom joinRoom(JoinRoomDto dto) {
        String roomCode = dto.getRoomCode().toUpperCase();
        ChatRoom room = roomStore.get(roomCode);
        
        if (room == null) {
            throw new RoomNotFoundException("Room not found: " + roomCode);
        }
        
        if (room.isExpired()) {
            roomStore.delete(roomCode);
            throw new RoomNotFoundException("Room has expired: " + roomCode);
        }
        
        if (room.isFull()) {
            throw new UnauthorizedException("Room is full (max " + room.getMaxUsers() + " users)");
        }
        
        // Generate unique user ID for joining user
        String userId = UUID.randomUUID().toString();
        
        // Add user to room
        boolean added = room.addUser(userId, dto.getUserName());
        if (!added) {
            throw new UnauthorizedException("Failed to join room");
        }
        
        // Update activity
        room.updateActivity(appConfig.getRoom().getInactivityTimeoutMinutes());
        roomStore.save(roomCode, room);
        
        log.info("User joined room - Code: {}, User: {}, Total users: {}", 
            roomCode, dto.getUserName(), room.getActiveUserIds().size());
        
        return room;
    }
    
    /**
     * Leave a chat room
     */
    public void leaveRoom(String roomCode, String userId) {
        ChatRoom room = roomStore.get(roomCode);
        
        if (room == null) {
            return; // Room doesn't exist, nothing to do
        }
        
        room.removeUser(userId);
        
        // If room is empty, delete it
        if (room.getActiveUserIds().isEmpty()) {
            roomStore.delete(roomCode);
            log.info("Room deleted (empty) - Code: {}", roomCode);
        } else {
            room.updateActivity(appConfig.getRoom().getInactivityTimeoutMinutes());
            roomStore.save(roomCode, room);
            log.info("User left room - Code: {}, Remaining users: {}", 
                roomCode, room.getActiveUserIds().size());
        }
    }
    
    /**
     * Get room information
     */
    public RoomInfoDto getRoomInfo(String roomCode) {
        ChatRoom room = roomStore.get(roomCode);
        
        if (room == null) {
            throw new RoomNotFoundException("Room not found: " + roomCode);
        }
        
        if (room.isExpired()) {
            roomStore.delete(roomCode);
            throw new RoomNotFoundException("Room has expired: " + roomCode);
        }
        
        return RoomInfoDto.builder()
            .roomCode(room.getRoomCode())
            .roomName(room.getRoomName())
            .currentUsers(room.getActiveUserIds().size())
            .maxUsers(room.getMaxUsers())
            .userNames(Set.copyOf(room.getUserIdToName().values()))
            .createdAt(room.getCreatedAt())
            .lastActivity(room.getLastActivity())
            .build();
    }
    
    /**
     * Check if room exists and is active
     */
    public boolean roomExists(String roomCode) {
        ChatRoom room = roomStore.get(roomCode);
        return room != null && !room.isExpired();
    }
    
    /**
     * Update room activity (called when messages are sent)
     */
    public void updateRoomActivity(String roomCode) {
        ChatRoom room = roomStore.get(roomCode);
        if (room != null) {
            room.updateActivity(appConfig.getRoom().getInactivityTimeoutMinutes());
            roomStore.save(roomCode, room);
        }
    }
}
