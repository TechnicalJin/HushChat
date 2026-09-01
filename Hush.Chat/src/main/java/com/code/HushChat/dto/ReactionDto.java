package com.code.HushChat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for adding/removing reactions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReactionDto {
    
    @NotBlank(message = "Room code is required")
    private String roomCode;
    
    @NotBlank(message = "Message ID is required")
    private String messageId;
    
    // userId is no longer accepted from the client — the server derives it
    // from the JWT principal. The field is kept for backwards-compatible
    // serialization only.
    private String userId;
    
    @NotBlank(message = "Emoji is required")
    private String emoji;
}
