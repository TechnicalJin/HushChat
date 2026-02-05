package com.code.HushChat.realtime;

import com.code.HushChat.model.event.RealtimeEvent;
import org.springframework.lang.Nullable;

/**
 * Transport abstraction for real-time event delivery.
 * Decouples business logic from delivery mechanism (Long Polling vs WebSocket).
 * 
 * Implementations:
 * - LongPollDispatcher: HTTP long polling (current)
 * - WebSocketDispatcher: WebSocket STOMP (future)
 * 
 * Only one implementation is active at runtime based on realtime.mode property.
 * 
 * @since 1.0.0
 */
public interface RealtimeDispatcher {
    
    /**
     * Dispatch a real-time event to all room members.
     * 
     * @param event The event to dispatch
     * @param excludeUserId Optional user ID to exclude from receiving the event (e.g., the sender)
     */
    void dispatch(RealtimeEvent event, @Nullable String excludeUserId);
}
