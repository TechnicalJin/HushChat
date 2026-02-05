package com.code.HushChat.realtime;

import com.code.HushChat.dto.PollEventDto;
import com.code.HushChat.model.event.RealtimeEvent;
import com.code.HushChat.service.LongPollManager;
import com.code.HushChat.util.EventConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Long Polling implementation of RealtimeDispatcher.
 * Active when realtime.mode=LONG_POLL (default).
 * 
 * Wraps existing LongPollManager without modifying its behavior.
 * Converts unified RealtimeEvent to PollEventDto for backward compatibility.
 * 
 * CRITICAL: This dispatcher MUST always remain functional.
 * It serves as the safety fallback for WebSocket failures.
 * 
 * @since 1.0.0
 */
@Component
@ConditionalOnProperty(
    prefix = "realtime",
    name = "mode",
    havingValue = "LONG_POLL",
    matchIfMissing = true
)
@RequiredArgsConstructor
@Slf4j
public class LongPollDispatcher implements RealtimeDispatcher {
    
    private final LongPollManager longPollManager;
    
    /**
     * Dispatch event via long polling mechanism.
     * Converts RealtimeEvent to PollEventDto and notifies pending polls.
     * 
     * SAFETY: Never throws exceptions - errors are logged and isolated.
     * 
     * @param event The real-time event to dispatch
     * @param excludeUserId Optional user ID to exclude from notification
     */
    @Override
    public void dispatch(RealtimeEvent event, @Nullable String excludeUserId) {
        try {
            if (event == null || event.getRoomCode() == null) {
                log.warn("Cannot dispatch null event or event without roomCode");
                return;
            }
            
            // Convert unified event model to legacy PollEventDto
            PollEventDto pollEvent = EventConverter.toPollEventDto(event);
            
            if (excludeUserId != null) {
                // Exclude specific user (e.g., sender)
                longPollManager.notifyRoomExcluding(
                    event.getRoomCode(),
                    excludeUserId,
                    java.util.List.of(pollEvent)
                );
            } else {
                // Broadcast to all room members
                longPollManager.notifyRoom(
                    event.getRoomCode(),
                    pollEvent
                );
            }
            
            log.debug("Dispatched {} event to room {} via long polling (exclude: {})",
                      event.getEventType(), event.getRoomCode(), excludeUserId);
                      
        } catch (Exception e) {
            // CRITICAL: Isolate failures - never propagate exceptions
            log.error("Long polling dispatch failed for event {} in room {}: {}", 
                     event.getEventType(), event.getRoomCode(), e.getMessage(), e);
            // Event will still be delivered via next poll cycle
        }
    }
}
