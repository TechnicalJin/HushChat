package com.code.HushChat.service;

import com.code.HushChat.config.AppConfig;
import com.code.HushChat.dto.FileUploadResponseDto;
import com.code.HushChat.dto.MessageResponseDto;
import com.code.HushChat.exception.UnauthorizedException;
import com.code.HushChat.model.ChatMessage;
import com.code.HushChat.model.ChatRoom;
import com.code.HushChat.model.FileMetadata;
import com.code.HushChat.realtime.RealtimeDispatcher;
import com.code.HushChat.storage.InMemoryFileStore;
import com.code.HushChat.storage.InMemoryMessageStore;
import com.code.HushChat.storage.InMemoryReactionStore;
import com.code.HushChat.storage.InMemoryRoomStore;
import com.code.HushChat.util.RateLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Service-level regression tests for SECURITY FIX #6.
 *
 * <p>Proves that the services key identity and ownership decisions off the
 * userId that the controller derives from the JWT principal (never from a
 * client-supplied field): message sender identity, message edit/unsend
 * ownership, reaction identity, reaction-deletion ownership, and file upload
 * identity all follow the caller's principal-derived userId.
 */
class MessageIdentitySecurityTest {

    private static final String ROOM = "ABCXY";
    private static final String ALICE = "alice-user";
    private static final String BOB = "bob-user";

    private InMemoryRoomStore roomStore;
    private InMemoryMessageStore messageStore;
    private InMemoryReactionStore reactionStore;
    private InMemoryFileStore fileStore;
    private RoomService roomService;
    private MessageService messageService;
    private ReactionService reactionService;
    private FileService fileService;
    private AppConfig appConfig;
    private RateLimiter rateLimiter;

    /** No-op dispatcher so real-time calls are harmless in unit tests. */
    private final RealtimeDispatcher noopDispatcher = (event, excludeUserId) -> { };

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        roomStore = new InMemoryRoomStore();
        messageStore = new InMemoryMessageStore();
        reactionStore = new InMemoryReactionStore();
        fileStore = new InMemoryFileStore();
        appConfig = new AppConfig();
        rateLimiter = new RateLimiter();

        roomService = new RoomService(roomStore, appConfig);

        messageService = new MessageService(
                messageStore, roomStore, roomService, appConfig, noopDispatcher, rateLimiter);

        reactionService = new ReactionService(reactionStore, roomStore, noopDispatcher);

        fileService = new FileService(
                fileStore, roomStore, messageStore, roomService, appConfig, rateLimiter, noopDispatcher);

        // Use the temp directory for file uploads to avoid polluting the workspace.
        appConfig.getFile().setUploadDir(tempDir.toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        if (tempDir != null && Files.exists(tempDir)) {
            // Best-effort cleanup of any uploaded files.
            try (var paths = Files.walk(tempDir)) {
                paths.filter(p -> !p.equals(tempDir)).forEach(p -> p.toFile().delete());
            }
        }
    }

    /** Create a room with ALICE and BOB as active members. */
    private void seedRoom() {
        ChatRoom room = new ChatRoom(ROOM, "Room", ALICE, "Alice", 10, 30);
        room.addUser(BOB, "Bob");
        roomStore.save(ROOM, room);
        roomStore.recordUserJoinTime(ROOM, ALICE);
        roomStore.recordUserJoinTime(ROOM, BOB);
    }

    /** Seed a text message owned by a given sender. */
    private void seedMessage(String messageId, String senderId) {
        messageStore.save(ROOM, ChatMessage.builder()
                .messageId(messageId)
                .roomCode(ROOM)
                .userId(senderId)
                .content("hello")
                .type(ChatMessage.MessageType.TEXT)
                .timestamp(LocalDateTime.now().minusMinutes(1))
                .expiryTime(LocalDateTime.now().plusMinutes(5))
                .lastModified(LocalDateTime.now())
                .build());
    }

    // ===== Message sender identity comes from Principal =====

    @Test
    void messageSenderIdentityComesFromPrincipalUserId() {
        seedRoom();
        // The controller passes principal.getName() as userId; the service must
        // attribute the message to that userId (not to any body field).
        MessageResponseDto sent = messageService.sendMessage(ROOM, ALICE, "hi");
        assertEquals(ALICE, sent.getSenderId());
        assertEquals("Alice", sent.getSenderName());

        ChatMessage stored = messageStore.getMessage(ROOM, sent.getMessageId());
        assertNotNull(stored);
        assertEquals(ALICE, stored.getUserId());
    }

    // ===== Message edit/unsend ownership uses Principal =====

    @Test
    void userCannotEditAnotherUsersMessage() {
        seedRoom();
        seedMessage("m-edit", ALICE);
        // BOB (principal) tries to edit ALICE's message -> rejected.
        assertThrows(UnauthorizedException.class,
                () -> messageService.editMessage(ROOM, "m-edit", BOB, "hacked"));
    }

    @Test
    void userCanEditOwnMessage() {
        seedRoom();
        seedMessage("m-edit", ALICE);
        MessageResponseDto updated = messageService.editMessage(ROOM, "m-edit", ALICE, "changed");
        assertTrue(updated.isEdited());
        assertEquals("changed", updated.getContent());
    }

    @Test
    void userCannotUnsendAnotherUsersMessage() {
        seedRoom();
        seedMessage("m-unsend", ALICE);
        assertThrows(UnauthorizedException.class,
                () -> messageService.unsendMessage(ROOM, "m-unsend", BOB));
    }

    @Test
    void userCanUnsendOwnMessage() {
        seedRoom();
        seedMessage("m-unsend", ALICE);
        MessageResponseDto unsent = messageService.unsendMessage(ROOM, "m-unsend", ALICE);
        assertTrue(unsent.isDeleted());
    }

    @Test
    void nonMemberCannotSendMessage() {
        seedRoom();
        assertThrows(UnauthorizedException.class,
                () -> messageService.sendMessage(ROOM, "outsider", "hi"));
    }

    // ===== Reaction identity comes from Principal =====

    @Test
    void reactionIdentityComesFromPrincipalUserId() {
        seedRoom();
        seedMessage("m-reaction", ALICE);
        reactionService.toggleReaction(ROOM, "m-reaction", ALICE, "❤️");

        // The reaction must be stored under ALICE's principal-derived userId.
        assertTrue(reactionStore.hasUserReacted(ROOM, "m-reaction", ALICE, "❤️"));
        // BOB's identity was not used.
        assertTrue(!reactionStore.hasUserReacted(ROOM, "m-reaction", BOB, "❤️"));
    }

    @Test
    void nonMemberCannotToggleReaction() {
        seedRoom();
        seedMessage("m-reaction", ALICE);
        assertThrows(UnauthorizedException.class,
                () -> reactionService.toggleReaction(ROOM, "m-reaction", "outsider", "❤️"));
    }

    @Test
    void reactionDeletionCannotDeleteAnotherUsersReaction() {
        seedRoom();
        seedMessage("m-reaction", ALICE);

        // ALICE reacts.
        reactionService.toggleReaction(ROOM, "m-reaction", ALICE, "❤️");
        assertTrue(reactionStore.hasUserReacted(ROOM, "m-reaction", ALICE, "❤️"));

        // BOB (principal) tries to remove ALICE's reaction. Removal is keyed by
        // the caller's userId, so ALICE's reaction must remain untouched.
        reactionService.removeReaction(ROOM, "m-reaction", BOB, "❤️");
        assertTrue(reactionStore.hasUserReacted(ROOM, "m-reaction", ALICE, "❤️"),
                "Another user must not be able to delete someone else's reaction");
    }

    // ===== File upload identity comes from Principal =====

    @Test
    void fileUploadIdentityComesFromPrincipalUserId() throws Exception {
        seedRoom();
        MockMultipartFile file = new MockMultipartFile(
                "file", "hello.txt", "text/plain", "hello world".getBytes());

        // The controller passes principal.getName() as userId and resolves
        // senderName server-side; the stored metadata must reflect that.
        FileUploadResponseDto dto = fileService.uploadFile(ROOM, ALICE, "Alice", file, null);

        assertEquals(ALICE, dto.getSenderId());
        assertEquals("Alice", dto.getSenderName());

        FileMetadata stored = fileStore.get(dto.getFileId());
        assertNotNull(stored);
        assertEquals(ROOM, stored.getRoomCode());
        assertEquals(ALICE, stored.getUserId());
    }

    @Test
    void nonMemberCannotUploadFile() {
        seedRoom();
        MockMultipartFile file = new MockMultipartFile(
                "file", "hello.txt", "text/plain", "hello world".getBytes());
        assertThrows(UnauthorizedException.class,
                () -> fileService.uploadFile(ROOM, "outsider", "Outsider", file, null));
    }
}

