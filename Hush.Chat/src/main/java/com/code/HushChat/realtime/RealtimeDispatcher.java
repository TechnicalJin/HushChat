package com.code.HushChat.realtime;

import com.code.HushChat.model.event.RealtimeEvent;
import org.springframework.lang.Nullable;

// NOTE: Long polling support intentionally removed.
// This interface now represents WebSocket-only event delivery.

/**
 * Interface for real-time event delivery via WebSocket.
 * 
 * Implementation:
 * - WebSocketDispatcher: WebSocket STOMP (the ONLY implementation)
 * 
 * @since 2.0.0 (WebSocket-only version)
 */
public interface RealtimeDispatcher {
    
    /**
     * Dispatch a real-time event to all room members via WebSocket.
     * 
     * @param event The event to dispatch
     * @param excludeUserId Optional user ID to exclude from receiving the event (e.g., the sender)
     */
    void dispatch(RealtimeEvent event, @Nullable String excludeUserId);
}
