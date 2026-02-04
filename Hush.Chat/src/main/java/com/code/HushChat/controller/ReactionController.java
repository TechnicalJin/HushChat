package com.code.HushChat.controller;

import com.code.HushChat.dto.ReactionDto;
import com.code.HushChat.dto.ReactionResponseDto;
import com.code.HushChat.dto.ReactionResponseDto.ReactionSummary;
import com.code.HushChat.model.ApiResponse;
import com.code.HushChat.service.ReactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for message reactions (emoji reactions).
 */
@RestController
@RequestMapping("/api/reactions")
@RequiredArgsConstructor
@Slf4j
public class ReactionController {
    
    private final ReactionService reactionService;
    
    /**
     * Toggle a reaction on a message (add if not exists, remove if exists)
     * POST /api/reactions/toggle
     */
    @PostMapping("/toggle")
    public ResponseEntity<ApiResponse<ReactionResponseDto>> toggleReaction(
            @Valid @RequestBody ReactionDto dto) {
        
        String roomCode = dto.getRoomCode().toUpperCase();
        
        log.info("Toggle reaction {} on message {} by user {}", 
                dto.getEmoji(), dto.getMessageId(), dto.getUserId());
        
        ReactionResponseDto response = reactionService.toggleReaction(
                roomCode,
                dto.getMessageId(),
                dto.getUserId(),
                dto.getEmoji()
        );
        
        return ResponseEntity.ok(
                ApiResponse.success("Reaction " + response.getAction(), response)
        );
    }
    
    /**
     * Add a reaction to a message
     * POST /api/reactions
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ReactionResponseDto>> addReaction(
            @Valid @RequestBody ReactionDto dto) {
        
        String roomCode = dto.getRoomCode().toUpperCase();
        
        log.info("Add reaction {} on message {} by user {}", 
                dto.getEmoji(), dto.getMessageId(), dto.getUserId());
        
        ReactionResponseDto response = reactionService.addReaction(
                roomCode,
                dto.getMessageId(),
                dto.getUserId(),
                dto.getEmoji()
        );
        
        return ResponseEntity.ok(
                ApiResponse.success("Reaction added", response)
        );
    }
    
    /**
     * Remove a reaction from a message
     * DELETE /api/reactions
     */
    @DeleteMapping
    public ResponseEntity<ApiResponse<ReactionResponseDto>> removeReaction(
            @RequestParam String roomCode,
            @RequestParam String messageId,
            @RequestParam String userId,
            @RequestParam String emoji) {
        
        roomCode = roomCode.toUpperCase();
        
        log.info("Remove reaction {} from message {} by user {}", emoji, messageId, userId);
        
        ReactionResponseDto response = reactionService.removeReaction(
                roomCode,
                messageId,
                userId,
                emoji
        );
        
        return ResponseEntity.ok(
                ApiResponse.success("Reaction removed", response)
        );
    }
    
    /**
     * Get reactions for a specific message
     * GET /api/reactions/{roomCode}/{messageId}
     */
    @GetMapping("/{roomCode}/{messageId}")
    public ResponseEntity<ApiResponse<ReactionSummary>> getReactions(
            @PathVariable String roomCode,
            @PathVariable String messageId) {
        
        roomCode = roomCode.toUpperCase();
        
        ReactionSummary summary = reactionService.getReactionSummary(roomCode, messageId);
        
        return ResponseEntity.ok(
                ApiResponse.success("Reactions retrieved", summary)
        );
    }
    
    /**
     * Get reactions for multiple messages (batch request)
     * POST /api/reactions/batch
     */
    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<Map<String, ReactionSummary>>> getReactionsBatch(
            @RequestParam String roomCode,
            @RequestBody List<String> messageIds) {
        
        roomCode = roomCode.toUpperCase();
        
        Map<String, ReactionSummary> summaries = reactionService.getReactionSummaries(roomCode, messageIds);
        
        return ResponseEntity.ok(
                ApiResponse.success("Reactions retrieved", summaries)
        );
    }
    
    /**
     * Get default quick reaction emojis
     * GET /api/reactions/defaults
     */
    @GetMapping("/defaults")
    public ResponseEntity<ApiResponse<List<String>>> getDefaultReactions() {
        return ResponseEntity.ok(
                ApiResponse.success("Default reactions", ReactionService.DEFAULT_REACTIONS)
        );
    }
}
