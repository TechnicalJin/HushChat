package com.code.HushChat.service;

import com.code.HushChat.config.AppConfig;
import com.code.HushChat.dto.JoinRoomDto;
import com.code.HushChat.exception.AlreadyInRoomException;
import com.code.HushChat.model.ChatMessage;
import com.code.HushChat.model.ChatRoom;
import com.code.HushChat.storage.InMemoryMessageStore;
import com.code.HushChat.storage.InMemoryRoomStore;
import com.code.HushChat.util.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RoomMembershipAndMessageRecoveryTest {

    private InMemoryRoomStore roomStore;
    private InMemoryMessageStore messageStore;
    private RoomService roomService;
    private MessageService messageService;

    @BeforeEach
    void setUp() {
        roomStore = new InMemoryRoomStore();
        AppConfig appConfig = new AppConfig();
        roomService = new RoomService(roomStore, appConfig);
        messageStore = new InMemoryMessageStore();

        messageService = new MessageService(
                messageStore,
                roomStore,
                roomService,
                appConfig,
                null,
                new RateLimiter()
        );
    }

    @Test
    void joinRoom_rejectsSameUserIdAlreadyPresent() {
        com.code.HushChat.dto.CreateRoomDto dto = new com.code.HushChat.dto.CreateRoomDto();
        dto.setRoomName("Room");
        dto.setUserName("Alice");
        dto.setMaxUsers(10);

        ChatRoom room = roomService.createRoom(dto);
        String userId = room.getCreatorId();

        JoinRoomDto joinDto = new JoinRoomDto();
        joinDto.setRoomCode(room.getRoomCode());
        joinDto.setUserName("AliceAgain");
        joinDto.setUserId(userId);

        AlreadyInRoomException ex = assertThrows(AlreadyInRoomException.class, () -> roomService.joinRoom(joinDto));
        assertTrue(ex.getMessage().contains("already a member"));
    }

    @Test
    void leaveRoom_isIdempotent_and_rejoin_reactivatesExistingMembership() {
        String roomCode = "ABCDE";
        String userId = "user-1";
        String otherUserId = "user-2";

        ChatRoom room = new ChatRoom(roomCode, "Room", userId, "Alice", 10, 30);
        room.addUser(otherUserId, "Bob");
        roomStore.save(roomCode, room);

        roomService.leaveRoom(roomCode, userId);
        assertFalse(roomStore.get(roomCode).isUserActive(userId));
        assertTrue(roomStore.get(roomCode).isUserActive(otherUserId));

        roomService.leaveRoom(roomCode, userId);
        assertFalse(roomStore.get(roomCode).isUserActive(userId));
        assertTrue(roomStore.get(roomCode).isUserActive(otherUserId));

        JoinRoomDto rejoin = new JoinRoomDto();
        rejoin.setRoomCode(roomCode);
        rejoin.setUserName("Alice");
        rejoin.setUserId(userId);

        ChatRoom rejoined = roomService.joinRoom(rejoin);
        assertTrue(rejoined.isUserActive(userId));
        assertEquals(2, rejoined.getActiveUserIds().size());
    }

    @Test
    void getActiveMessages_returnsOnlyUnexpiredMessages() {
        String roomCode = "ABCDE";
        String userId = "user-1";
        String otherUser = "user-2";

        ChatRoom room = new ChatRoom(roomCode, "Room", userId, "Alice", 10, 30);
        room.addUser(otherUser, "Bob");
        roomStore.save(roomCode, room);
        roomStore.recordUserJoinTime(roomCode, userId);
        roomStore.recordUserJoinTime(roomCode, otherUser);

        LocalDateTime now = LocalDateTime.now();
        messageStore.save(roomCode, ChatMessage.builder()
                .messageId("good")
                .roomCode(roomCode)
                .userId(userId)
                .content("still valid")
                .type(ChatMessage.MessageType.TEXT)
                .timestamp(now.minusMinutes(1))
                .expiryTime(now.plusMinutes(5))
                .build());

        messageStore.save(roomCode, ChatMessage.builder()
                .messageId("expired")
                .roomCode(roomCode)
                .userId(otherUser)
                .content("expired")
                .type(ChatMessage.MessageType.TEXT)
                .timestamp(now.minusMinutes(20))
                .expiryTime(now.minusMinutes(1))
                .build());

        List<com.code.HushChat.dto.MessageResponseDto> messages = messageService.getActiveMessages(roomCode, userId);

        assertEquals(1, messages.size());
        assertEquals("good", messages.get(0).getMessageId());
    }
}
