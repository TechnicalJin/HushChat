package com.code.HushChat.service;

import com.code.HushChat.dto.PollEventDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages pending long-poll requests per room.
 * When a new message or reaction event occurs, all pending polls for that room are notified.
 * 
 * Thread-safe implementation supporting:
 * - Registration of pending poll requests
 * - Notification when events occur (messages, reactions)
 * - Timeout handling
 */
@Component
@Slf4j
public class LongPollManager {
    
    /**
     * Represents a pending poll request waiting for events
     */
    public static class PendingPoll {
        private final String pollId;
        private final String roomCode;
        private final String userId;
        private final CompletableFuture<List<PollEventDto>> future;
        private final long registeredAt;
        
        public PendingPoll(String pollId, String roomCode, String userId) {
            this.pollId = pollId;
            this.roomCode = roomCode;
            this.userId = userId;
            this.future = new CompletableFuture<>();
            this.registeredAt = System.currentTimeMillis();
        }
        
        public String getPollId() { return pollId; }
        public String getRoomCode() { return roomCode; }
        public String getUserId() { return userId; }
        public CompletableFuture<List<PollEventDto>> getFuture() { return future; }
        public long getRegisteredAt() { return registeredAt; }
    }
    
    // Map: roomCode -> Set of pending polls
    private final Map<String, Set<PendingPoll>> pendingPollsByRoom = new ConcurrentHashMap<>();
    
    // Lock for thread-safe operations
    private final ReentrantLock lock = new ReentrantLock();
    
    // Executor for timeout handling
    private final ScheduledExecutorService timeoutExecutor = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "poll-timeout-handler");
        t.setDaemon(true);
        return t;
    });
    
    /**
     * Register a new pending poll request for a room.
     * Returns a CompletableFuture that will be completed when events arrive or timeout occurs.
     * 
     * @param roomCode The room to listen for events
     * @param userId The user making the poll request
     * @param timeoutSeconds How long to wait before returning empty
     * @return PendingPoll object containing the future
     */
    public PendingPoll registerPoll(String roomCode, String userId, int timeoutSeconds) {
        String pollId = UUID.randomUUID().toString();
        PendingPoll pendingPoll = new PendingPoll(pollId, roomCode, userId);
        
        lock.lock();
        try {
            pendingPollsByRoom
                .computeIfAbsent(roomCode, k -> ConcurrentHashMap.newKeySet())
                .add(pendingPoll);
        } finally {
            lock.unlock();
        }
        
        // Schedule timeout
        timeoutExecutor.schedule(() -> {
            if (!pendingPoll.getFuture().isDone()) {
                // Complete with empty list on timeout
                pendingPoll.getFuture().complete(Collections.emptyList());
                removePoll(roomCode, pendingPoll);
            }
        }, timeoutSeconds, TimeUnit.SECONDS);
        
        log.debug("Registered poll {} for room {} by user {}", pollId, roomCode, userId);
        return pendingPoll;
    }
    
    /**
     * Notify all pending polls for a room with events.
     * All waiting requests will receive the events and return to clients.
     * 
     * @param roomCode The room where events occurred
     * @param events List of events to send to all pending polls
     */
    public void notifyRoom(String roomCode, List<PollEventDto> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        
        Set<PendingPoll> polls;
        lock.lock();
        try {
            polls = pendingPollsByRoom.remove(roomCode);
        } finally {
            lock.unlock();
        }
        
        if (polls != null && !polls.isEmpty()) {
            log.debug("Notifying {} pending polls for room {} with {} events", 
                     polls.size(), roomCode, events.size());
            
            for (PendingPoll poll : polls) {
                if (!poll.getFuture().isDone()) {
                    poll.getFuture().complete(events);
                }
            }
        }
    }
    
    /**
     * Notify all pending polls for a room with a single event.
     * 
     * @param roomCode The room where the event occurred
     * @param event The event to send
     */
    public void notifyRoom(String roomCode, PollEventDto event) {
        notifyRoom(roomCode, List.of(event));
    }
    
    /**
     * Notify a specific user's poll in a room (excludes the triggering user).
     * This is useful when you want to exclude the user who triggered the event.
     * 
     * @param roomCode The room where events occurred
     * @param excludeUserId User ID to exclude from notification
     * @param events Events to send
     */
    public void notifyRoomExcluding(String roomCode, String excludeUserId, List<PollEventDto> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        
        Set<PendingPoll> polls;
        lock.lock();
        try {
            polls = pendingPollsByRoom.get(roomCode);
            if (polls == null) {
                return;
            }
            // Create a new set for remaining polls
            Set<PendingPoll> remaining = ConcurrentHashMap.newKeySet();
            Set<PendingPoll> toNotify = ConcurrentHashMap.newKeySet();
            
            for (PendingPoll poll : polls) {
                if (excludeUserId != null && excludeUserId.equals(poll.getUserId())) {
                    remaining.add(poll);
                } else {
                    toNotify.add(poll);
                }
            }
            
            if (remaining.isEmpty()) {
                pendingPollsByRoom.remove(roomCode);
            } else {
                pendingPollsByRoom.put(roomCode, remaining);
            }
            
            polls = toNotify;
        } finally {
            lock.unlock();
        }
        
        if (!polls.isEmpty()) {
            log.debug("Notifying {} pending polls for room {} (excluding user {})", 
                     polls.size(), roomCode, excludeUserId);
            
            for (PendingPoll poll : polls) {
                if (!poll.getFuture().isDone()) {
                    poll.getFuture().complete(events);
                }
            }
        }
    }
    
    /**
     * Remove a pending poll from tracking
     */
    private void removePoll(String roomCode, PendingPoll poll) {
        lock.lock();
        try {
            Set<PendingPoll> polls = pendingPollsByRoom.get(roomCode);
            if (polls != null) {
                polls.remove(poll);
                if (polls.isEmpty()) {
                    pendingPollsByRoom.remove(roomCode);
                }
            }
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Get count of pending polls for a room (for monitoring)
     */
    public int getPendingPollCount(String roomCode) {
        Set<PendingPoll> polls = pendingPollsByRoom.get(roomCode);
        return polls != null ? polls.size() : 0;
    }
    
    /**
     * Get total pending polls across all rooms (for monitoring)
     */
    public int getTotalPendingPollCount() {
        return pendingPollsByRoom.values().stream()
                .mapToInt(Set::size)
                .sum();
    }
    
    /**
     * Clean up pending polls for a room (when room is deleted)
     */
    public void cleanupRoom(String roomCode) {
        Set<PendingPoll> polls;
        lock.lock();
        try {
            polls = pendingPollsByRoom.remove(roomCode);
        } finally {
            lock.unlock();
        }
        
        if (polls != null) {
            for (PendingPoll poll : polls) {
                poll.getFuture().cancel(false);
            }
            log.debug("Cleaned up {} pending polls for room {}", polls.size(), roomCode);
        }
    }
}
