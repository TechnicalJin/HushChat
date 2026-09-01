package com.code.HushChat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageDto {
    
    @NotBlank(message = "Room code is required")
    private String roomCode;
    
    // userId is no longer accepted from the client — the server derives it
    // from the JWT principal. The field is kept for backwards-compatible
    // serialization only.
    private String userId;
    
    @NotBlank(message = "Message content is required")
    @Size(min = 1, max = 5000, message = "Message must be between 1 and 5000 characters")
    private String content;
    
    // Optional: Reply metadata when replying to another message
    private ReplyToRequest replyTo;
    
    /**
     * Request DTO for reply metadata
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReplyToRequest {
        private String messageId;      // ID of the message being replied to
        private String senderId;       // Sender ID of original message
        private String senderName;     // Sender name of original message
        private String messageType;    // Type of original message (TEXT, FILE)
        private String previewText;    // Preview text of original message
    }
}
