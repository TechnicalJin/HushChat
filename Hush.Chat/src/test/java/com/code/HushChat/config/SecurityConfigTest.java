package com.code.HushChat.config;

import com.code.HushChat.model.ChatMessage;
import com.code.HushChat.model.ChatRoom;
import com.code.HushChat.model.FileMetadata;
import com.code.HushChat.storage.InMemoryFileStore;
import com.code.HushChat.storage.InMemoryMessageStore;
import com.code.HushChat.storage.InMemoryRoomStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression test for SECURITY FIX #3: verifies that Spring Boot's public
 * static frontend resources are explicitly permitted anonymously while the
 * deny-by-default rule and API protection remain intact.
 *
 * <p>Verifies:
 * <ul>
 *   <li>GET / and the frontend entry point are publicly accessible</li>
 *   <li>static CSS/JS resources are publicly accessible</li>
 *   <li>an unknown/unmatched endpoint remains denied (deny-by-default)</li>
 *   <li>/api/dev/** is NOT publicly accessible</li>
 *   <li>/api/** protection is not weakened</li>
 * </ul>
 *
 * @since 3.0.0
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "app.jwt-secret=daf66e01593f61a15b857cf433aae03a005812b31234e149036bcc8dee755dbb",
    "app.jwt-expiration-milliseconds=3600000",
    "cors.allowed-origins=http://localhost:8080"
})
class SecurityConfigTest {

    public static final String ROOM = "ABCXY";
    public static final String OWNER = "owner-user";
    public static final String OTHER = "other-user";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InMemoryRoomStore roomStore;

    @Autowired
    private InMemoryMessageStore messageStore;

    @Autowired
    private InMemoryFileStore fileStore;

    @BeforeEach
    void cleanStores() {
        roomStore.clear();
        messageStore.clear();
        fileStore.clear();
    }

    @AfterEach
    void clearStoresAfter() {
        roomStore.clear();
        messageStore.clear();
        fileStore.clear();
    }

    /** Seed a room whose creator is OWNER and which also has OTHER as a member. */
    private void seedRoom() {
        ChatRoom room = new ChatRoom(ROOM, "Room", OWNER, "Owner", 10, 30);
        room.addUser(OTHER, "Other");
        roomStore.save(ROOM, room);
        roomStore.recordUserJoinTime(ROOM, OWNER);
        roomStore.recordUserJoinTime(ROOM, OTHER);
    }

    /** Seed a non-expired chat message in the room owned by a given sender. */
    private void seedMessage(String messageId, String senderId) {
        ChatMessage message = ChatMessage.builder()
                .messageId(messageId)
                .roomCode(ROOM)
                .userId(senderId)
                .content("hello")
                .type(ChatMessage.MessageType.TEXT)
                .timestamp(LocalDateTime.now().minusMinutes(1))
                .expiryTime(LocalDateTime.now().plusMinutes(5))
                .edited(false)
                .deleted(false)
                .lastModified(LocalDateTime.now())
                .build();
        messageStore.save(ROOM, message);
    }

    /** Seed a non-expired file metadata record belonging to a room. */
    private void seedFile(String fileId, String roomCode) {
        FileMetadata metadata = FileMetadata.builder()
                .fileId(fileId)
                .roomCode(roomCode)
                .userId(OWNER)
                .originalFilename("doc.txt")
                .storedFilename("stored.txt")
                .contentType("text/plain")
                .fileSize(10)
                .uploadTime(LocalDateTime.now())
                .expiryTime(LocalDateTime.now().plusMinutes(5))
                .build();
        fileStore.save(fileId, metadata);
    }

    /** Frontend entry point (serves index.html) must be publicly accessible. */
    @Test
    void frontendEntryPointIsPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is(HttpStatus.OK.value()));
        mockMvc.perform(get("/index.html"))
                .andExpect(status().is(HttpStatus.OK.value()));
    }

    /** All static frontend pages must be publicly accessible. */
    @Test
    void frontendPagesArePubliclyAccessible() throws Exception {
        mockMvc.perform(get("/chat.html"))
                .andExpect(status().is(HttpStatus.OK.value()));
        mockMvc.perform(get("/otp.html"))
                .andExpect(status().is(HttpStatus.OK.value()));
    }

    /** Static CSS resources must be publicly accessible. */
    @Test
    void staticCssResourcesArePubliclyAccessible() throws Exception {
        mockMvc.perform(get("/css/common.css"))
                .andExpect(status().is(HttpStatus.OK.value()));
    }

    /** Static JS resources must be publicly accessible. */
    @Test
    void staticJsResourcesArePubliclyAccessible() throws Exception {
        mockMvc.perform(get("/js/config.js"))
                .andExpect(status().is(HttpStatus.OK.value()));
    }

    /** Unknown/unmatched endpoints must remain denied (deny-by-default). */
    @Test
    void unknownEndpointRemainsDenied() throws Exception {
        mockMvc.perform(get("/no-such-resource"))
                .andExpect(status().is(HttpStatus.UNAUTHORIZED.value()));
    }

    /** /api/dev/** must remain NOT publicly accessible. */
    @Test
    void devApiRemainsNotPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/api/dev/anything"))
                .andExpect(status().is(HttpStatus.UNAUTHORIZED.value()));
    }

    /** /api/** protection must not be weakened (unauthenticated requests rejected). */
    @Test
    void apiProtectionIsNotWeakened() throws Exception {
        mockMvc.perform(get("/api/rooms/messages"))
                .andExpect(status().is(HttpStatus.UNAUTHORIZED.value()));
    }

    // ===== SECURITY FIX #6: server-derived identity =====
    // Messages, files, and reaction mutation/query endpoints now require
    // authentication so identity (userId) is always derived from the JWT
    // principal rather than a client-supplied field.

    /** Message endpoints require authentication (identity comes from JWT). */
    @Test
    void messageEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/messages/ABCDE/active"))
                .andExpect(status().is(HttpStatus.UNAUTHORIZED.value()));
        mockMvc.perform(get("/api/messages/ABCDE"))
                .andExpect(status().is(HttpStatus.UNAUTHORIZED.value()));
    }

    /** File endpoints require authentication (upload + download). */
    @Test
    void fileEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/files/some-file-id/download"))
                .andExpect(status().is(HttpStatus.UNAUTHORIZED.value()));
    }

    /** Reaction mutation/query endpoints require authentication. */
    @Test
    void reactionEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/reactions/ABCDE/msg-1"))
                .andExpect(status().is(HttpStatus.UNAUTHORIZED.value()));
        mockMvc.perform(get("/api/reactions/batch"))
                .andExpect(status().is(HttpStatus.UNAUTHORIZED.value()));
    }

    /** The static reaction-defaults list remains public (no auth needed). */
    @Test
    void reactionDefaultsArePubliclyAccessible() throws Exception {
        mockMvc.perform(get("/api/reactions/defaults"))
                .andExpect(status().is(HttpStatus.OK.value()));
    }

    // ----- Fix #6 enhanced security tests -----

    /** An authenticated principal can send a message to a room they belong to. */
    @Test
    @WithMockUser(OWNER)
    void authenticatedUserCanSendMessage() throws Exception {
        seedRoom();
        mockMvc.perform(post("/api/messages/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomCode\":\"" + ROOM + "\",\"content\":\"hi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roomCode", is(ROOM)))
                .andExpect(jsonPath("$.data.senderId", is(OWNER)));
    }

    /**
     * A client-supplied "userId" in the request body must NOT influence the
     * sender identity. The message must be attributed to the JWT principal.
     */
    @Test
    @WithMockUser(OWNER)
    void spoofedUserIdInBodyDoesNotImpersonateSender() throws Exception {
        seedRoom();
        mockMvc.perform(post("/api/messages/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomCode\":\"" + ROOM
                                + "\",\"userId\":\"intruder\",\"content\":\"hi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.senderId", is(OWNER)));
    }

    /** Only the message owner (from Principal) may edit it; another member cannot. */
    @Test
    @WithMockUser(OTHER)
    void messageEditOwnershipUsesPrincipal() throws Exception {
        seedRoom();
        seedMessage("msg-1", OWNER);

        // OTHER (principal) tries to edit OWNER's message -> rejected (401).
        mockMvc.perform(put("/api/messages/" + ROOM + "/msg-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hacked\"}"))
                .andExpect(status().is(HttpStatus.UNAUTHORIZED.value()));
    }

    /** The owner (from Principal) may edit their own message. */
    @Test
    @WithMockUser(OWNER)
    void messageOwnerCanEditOwnMessage() throws Exception {
        seedRoom();
        seedMessage("msg-1", OWNER);
        mockMvc.perform(put("/api/messages/" + ROOM + "/msg-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", is("updated")))
                .andExpect(jsonPath("$.data.edited", is(true)));
    }

    /** Only the message owner (from Principal) may unsend it; another member cannot. */
    @Test
    @WithMockUser(OTHER)
    void messageUnsendOwnershipUsesPrincipal() throws Exception {
        seedRoom();
        seedMessage("msg-2", OWNER);
        mockMvc.perform(delete("/api/messages/" + ROOM + "/msg-2"))
                .andExpect(status().is(HttpStatus.UNAUTHORIZED.value()));
    }

    /** A non-member authenticated user is rejected when sending to a room. */
    @Test
    @WithMockUser("outsider")
    void nonMemberCannotSendMessage() throws Exception {
        seedRoom();
        mockMvc.perform(post("/api/messages/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomCode\":\"" + ROOM + "\",\"content\":\"hi\"}"))
                .andExpect(status().is(HttpStatus.UNAUTHORIZED.value()));
    }

    /** Room create/join/leave/info/exists stay public (no auth required). */
    @Test
    void roomEndpointsRemainPublic() throws Exception {
        // info of a non-existent room returns 404 (NOT 401) -> endpoint is public.
        mockMvc.perform(get("/api/rooms/ZZZZZ/info"))
                .andExpect(status().is(HttpStatus.NOT_FOUND.value()));
        mockMvc.perform(get("/api/rooms/ZZZZZ/exists"))
                .andExpect(status().isOk());
        // create with invalid body -> 400 (NOT 401) proves the endpoint is public.
        mockMvc.perform(post("/api/rooms/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is(HttpStatus.BAD_REQUEST.value()));
        mockMvc.perform(post("/api/rooms/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is(HttpStatus.BAD_REQUEST.value()));
        mockMvc.perform(post("/api/rooms/leave")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is(HttpStatus.BAD_REQUEST.value()));
    }

    /** An authenticated non-member is forbidden from downloading a room's file. */
    @Test
    @WithMockUser("outsider")
    void nonMemberCannotDownloadFile() throws Exception {
        seedRoom();
        seedFile("file-1", ROOM);
        mockMvc.perform(get("/api/files/file-1/download"))
                .andExpect(status().isForbidden());
    }

    /** A member of the room can download a valid (non-expired) file. */
    @Test
    @WithMockUser(OWNER)
    void memberCanDownloadFile() throws Exception {
        seedRoom();
        // Create a real temp file so the FileSystemResource exists on disk.
        Path tempFile = Files.createTempFile("download-test", ".txt");
        Files.writeString(tempFile, "file contents");
        try {
            FileMetadata metadata = FileMetadata.builder()
                    .fileId("file-2")
                    .roomCode(ROOM)
                    .userId(OWNER)
                    .originalFilename("doc.txt")
                    .storedFilename("stored.txt")
                    .contentType("text/plain")
                    .fileSize(13)
                    .filePath(tempFile.toString())
                    .uploadTime(LocalDateTime.now())
                    .expiryTime(LocalDateTime.now().plusMinutes(5))
                    .build();
            fileStore.save("file-2", metadata);

            mockMvc.perform(get("/api/files/file-2/download"))
                    .andExpect(status().isOk());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

// =======================================================================
    // SECURITY FIX #9C — Security response headers
    // =======================================================================

    /** Every response must carry HSTS (HTTPS-only) enforcement headers. */
    @Test
    void httpStrictTransportSecurityHeaderIsPresent() throws Exception {
        mockMvc.perform(get("/").secure(true))
                .andExpect(status().isOk())
                .andExpect(header().string("Strict-Transport-Security",
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("max-age=31536000"),
                                org.hamcrest.Matchers.containsString("includeSubDomains"),
                                org.hamcrest.Matchers.containsString("preload"))));
    }

    /** Every response must enforce a restrictive Content-Security-Policy. */
    @Test
    void contentSecurityPolicyHeaderIsPresent() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy",
                        org.hamcrest.Matchers.containsString("default-src 'self'")));
    }

    /** X-Content-Type-Options: nosniff must prevent MIME sniffing. */
    @Test
    void xContentTypeOptionsHeaderIsNoSniff() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    /** X-Frame-Options: DENY must block clickjacking. */
    @Test
    void xFrameOptionsHeaderDeniesEmbedding() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    /** Referrer-Policy must restrict what is leaked via the Referer header. */
    @Test
    void referrerPolicyHeaderRestrictsRefererLeakage() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(header().string("Referrer-Policy",
                        "strict-origin-when-cross-origin"));
    }

    // =======================================================================
    // SECURITY FIX #9D — Actuator endpoint protection
    // =======================================================================

    /** /actuator/health remains publicly reachable (for load balancers). */
    @Test
    void actuatorHealthIsPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().is(HttpStatus.OK.value()));
    }

    /** Sensitive actuator endpoints (env, configprops, etc.) require auth. */
    @Test
    void sensitiveActuatorEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().is(HttpStatus.UNAUTHORIZED.value()));
        mockMvc.perform(get("/actuator/configprops"))
                .andExpect(status().is(HttpStatus.UNAUTHORIZED.value()));
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().is(HttpStatus.UNAUTHORIZED.value()));
        mockMvc.perform(get("/actuator/beans"))
                .andExpect(status().is(HttpStatus.UNAUTHORIZED.value()));
    }
    // =======================================================================
    // SECURITY FIX #7 — Default Spring Security user is not used
    // =======================================================================

    /** HTTP Basic authentication is disabled; even with valid credentials the
     *  request is rejected (401) rather than processed through Basic auth. */
    @Test
    void httpBasicAuthenticationIsDisabled() throws Exception {
        // Perform a Base64-encoded "user:password" Basic auth header request.
        // Because SecurityConfig disables httpBasic(), the server must NOT
        // authenticate via the Spring default user — it should reject the
        // request because no Bearer JWT is present.
        String credentials = java.util.Base64.getEncoder()
                .encodeToString("user:password".getBytes());
        mockMvc.perform(get("/api/messages/ANY-ROOM")
                        .header("Authorization", "Basic " + credentials))
                .andExpect(status().isUnauthorized());
    }

    /** Form login is disabled; posting to a /login endpoint is not
     *  processed through form authentication. The unauthenticated request
     *  falls to deny-by-default and is rejected (401 Unauthorized). */
    @Test
    void formLoginIsDisabled() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "admin")
                        .param("password", "secret"))
                .andExpect(status().isUnauthorized());
    }

    // =======================================================================
    // SECURITY FIX #7 — File download authorization
    // =======================================================================

    /** A former (left) member is forbidden from downloading a room's file. */
    @Test
    @WithMockUser("former-member")
    void formerMemberCannotDownloadFile() throws Exception {
        // Build a room containing the former member, then remove them.
        ChatRoom room = new ChatRoom(ROOM, "Room", OWNER, "Owner", 10, 30);
        room.addUser("former-member", "Former");
        roomStore.save(ROOM, room);
        room.removeUser("former-member"); // user left the room
        seedFile("file-3", ROOM);

        mockMvc.perform(get("/api/files/file-3/download"))
                .andExpect(status().isForbidden());
    }
}
