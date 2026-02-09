/**
 * EventProcessor - Centralized event handling with deduplication
 * 
 * PURPOSE:
 * - Single entry point for ALL WebSocket events
 * - Enforces event deduplication by eventId
 * - Routes events to existing UI handlers (chat.js, emojiSystem)
 * - Transport-agnostic: does NOT contain WebSocket implementation details
 * 
 * NOTE: Long polling support intentionally removed.
 * WebSocket is the ONLY real-time transport mechanism.
 * 
 * INVARIANTS:
 * - Same event delivered twice = processed ONCE
 * - Bounded cache prevents memory leaks
 * 
 * @since 2.0.0 (WebSocket-only version)
 */
const eventProcessor = {

    // Configuration
    MAX_CACHE_SIZE: 1000,
    CACHE_TTL_MS: 15 * 60 * 1000, // 15 minutes (messages expire in 10)

    // State
    processedEventIds: new Map(), // eventId -> timestamp
    initialized: false,

    /**
     * Initialize the event processor
     */
    init() {
        if (this.initialized) {
            return;
        }

        // Start periodic cache cleanup (every 5 minutes)
        setInterval(() => this.cleanupCache(), 5 * 60 * 1000);

        this.initialized = true;
        console.log('[EventProcessor] Initialized with cache size limit:', this.MAX_CACHE_SIZE);
    },

    /**
     * Process a single event from ANY transport.
     * Enforces deduplication before routing to UI handlers.
     * 
     * @param {Object} event - Event object from WebSocket
     * @returns {boolean} - true if processed, false if duplicate
     */
    processEvent(event) {
        if (!event) {
            console.warn('[EventProcessor] Received null event');
            return false;
        }

        // Generate eventId if missing (backward compatibility)
        const eventId = this.getEventId(event);

        // Check for duplicate
        if (this.isDuplicate(eventId)) {
            console.debug('[EventProcessor] Duplicate event ignored:', eventId);
            return false;
        }

        // Mark as processed
        this.markProcessed(eventId);

        // Route to appropriate handler based on event type
        this.routeEvent(event);

        return true;
    },

    /**
     * Process multiple events from a batch.
     * 
     * @param {Array} events - Array of events
     * @returns {number} - Count of events actually processed (non-duplicates)
     */
    processEvents(events) {
        if (!events || !Array.isArray(events)) {
            return 0;
        }

        let processedCount = 0;
        for (const event of events) {
            if (this.processEvent(event)) {
                processedCount++;
            }
        }

        if (processedCount > 0) {
            console.log(`[EventProcessor] Processed ${processedCount}/${events.length} events (${events.length - processedCount} duplicates)`);
        }

        return processedCount;
    },

    /**
     * Extract or generate a unique event ID.
     * Uses multiple fallback strategies for backward compatibility.
     * 
     * @param {Object} event - Event object
     * @returns {string} - Unique event identifier
     */
    getEventId(event) {
        // Priority 1: Explicit eventId from server
        if (event.eventId) {
            return event.eventId;
        }

        // Priority 2: Construct from type + messageId + timestamp
        if (event.messageId) {
            const type = event.type || 'MESSAGE';
            const ts = event.timestamp || event.lastModified || '';
            return `${type}-${event.messageId}-${ts}`;
        }

        // Priority 3: For message objects inside events
        if (event.message?.messageId) {
            const type = event.type || 'MESSAGE';
            const ts = event.message.timestamp || event.message.lastModified || '';
            return `${type}-${event.message.messageId}-${ts}`;
        }

        // Priority 4: For reaction events
        if (event.type === 'REACTION' && event.messageId) {
            const emoji = event.emoji || '';
            const userId = event.reactedByUserId || '';
            return `REACTION-${event.messageId}-${emoji}-${userId}`;
        }

        // Fallback: Generate from content hash (not ideal but prevents duplicates)
        const content = JSON.stringify(event);
        return `HASH-${this.simpleHash(content)}`;
    },

    /**
     * Check if an event has already been processed.
     * 
     * @param {string} eventId - Event identifier
     * @returns {boolean} - true if already processed
     */
    isDuplicate(eventId) {
        return this.processedEventIds.has(eventId);
    },

    /**
     * Mark an event as processed.
     * 
     * @param {string} eventId - Event identifier
     */
    markProcessed(eventId) {
        // Enforce cache size limit (remove oldest entries)
        if (this.processedEventIds.size >= this.MAX_CACHE_SIZE) {
            this.evictOldest();
        }

        this.processedEventIds.set(eventId, Date.now());
    },

    /**
     * Route event to appropriate UI handler.
     * Calls EXISTING handlers - does NOT implement new UI logic.
     * 
     * @param {Object} event - Event object
     */
    routeEvent(event) {
        const eventType = event.type || 'MESSAGE';

        switch (eventType) {
            case 'MESSAGE':
            case 'MESSAGE_EDIT':
            case 'MESSAGE_DELETE':
                this.handleMessageEvent(event);
                break;

            case 'REACTION':
                this.handleReactionEvent(event);
                break;
            
            case 'PRESENCE':
                this.handlePresenceEvent(event);
                break;

            case 'ROOM_CLOSED':
                if (typeof chat !== 'undefined' && chat.handleRoomClosed) {
                    chat.handleRoomClosed();
                }
                break;

            default:
                console.warn('[EventProcessor] Unknown event type:', eventType);
        }
    },

    /**
     * Handle message-related events.
     * Routes to existing chat.js handlers.
     * 
     * @param {Object} event - Message event
     */
    handleMessageEvent(event) {
        // Extract message from event (may be nested or flat)
        const message = event.message || event;

        if (!message || !message.messageId) {
            console.warn('[EventProcessor] Invalid message event:', event);
            return;
        }

        // Route to existing chat.js rendering
        if (typeof chat !== 'undefined') {
            const eventType = event.type || 'MESSAGE';

            if (eventType === 'MESSAGE_DELETE' && message.deleted) {
                // Handle deleted message
                if (typeof chat.handleMessageDeleted === 'function') {
                    chat.handleMessageDeleted(message.messageId);
                } else {
                    chat.renderMessage(message);
                }
            } else if (eventType === 'MESSAGE_EDIT' && message.edited) {
                // Handle edited message
                if (typeof chat.updateMessage === 'function') {
                    chat.updateMessage(message);
                } else {
                    chat.renderMessage(message);
                }
            } else {
                // New message
                chat.renderMessage(message);
            }
        }
    },

    /**
     * Handle reaction events.
     * Routes to existing emojiSystem handlers.
     * 
     * @param {Object} event - Reaction event
     */
    handleReactionEvent(event) {
        if (typeof emojiSystem === 'undefined') {
            console.warn('[EventProcessor] emojiSystem not available for reaction event');
            return;
        }

        const { messageId, emoji, action, updatedReactionCounts, reactedByUserId } = event;

        if (!messageId) {
            console.warn('[EventProcessor] Reaction event missing messageId');
            return;
        }

        // Update reaction UI
        if (updatedReactionCounts) {
            // Full reaction counts update via emojiSystem
            this.updateMessageReactions(messageId, updatedReactionCounts);
        } else {
            // Single reaction update
            emojiSystem.updateMessageReactions(messageId, emoji, action, reactedByUserId);
        }
    },

    /**
     * Update message reactions in the UI.
     * 
     * @param {string} messageId - Message ID
     * @param {Object} reactionCounts - Map of emoji to count
     */
    updateMessageReactions(messageId, reactionCounts) {
        if (typeof emojiSystem === 'undefined') {
            console.warn('[EventProcessor] emojiSystem not available');
            return;
        }

        // Find the message element and update reactions
        const messageEl = document.querySelector(`[data-message-id="${messageId}"]`);
        if (!messageEl) {
            console.debug('[EventProcessor] Message element not found:', messageId);
            return;
        }

        // Get current user ID for highlighting active reactions
        const currentUserId = localStorage.getItem('userId');

        // Use emojiSystem.renderMessageReactions (the method that actually exists)
        if (typeof emojiSystem.renderMessageReactions === 'function') {
            emojiSystem.renderMessageReactions(messageEl, reactionCounts, currentUserId);
        } else {
            // Fallback: Manual render
            console.warn('[EventProcessor] renderMessageReactions not available, using manual render');
            this.manualRenderReactions(messageEl, messageId, reactionCounts, currentUserId);
        }
    },

    /**
     * Manual fallback for rendering reactions when emojiSystem is unavailable
     */
    manualRenderReactions(messageEl, messageId, reactionCounts, currentUserId) {
        let reactionsContainer = messageEl.querySelector('.message-reactions');
        
        // Remove empty reactions
        if (!reactionCounts || Object.keys(reactionCounts).length === 0) {
            if (reactionsContainer) {
                reactionsContainer.style.display = 'none';
            }
            return;
        }

        // Create container if it doesn't exist
        if (!reactionsContainer) {
            reactionsContainer = document.createElement('div');
            reactionsContainer.className = 'message-reactions';
            
            const footer = messageEl.querySelector('.message-footer');
            if (footer) {
                footer.parentNode.insertBefore(reactionsContainer, footer);
            } else {
                messageEl.appendChild(reactionsContainer);
            }
        }

        // Clear and rebuild
        reactionsContainer.innerHTML = '';
        reactionsContainer.style.display = '';

        // Render each reaction
        for (const [emoji, data] of Object.entries(reactionCounts)) {
            const isActive = data.userIds && data.userIds.includes(currentUserId);
            const badge = document.createElement('button');
            badge.className = `reaction-badge${isActive ? ' active' : ''}`;
            badge.dataset.emoji = emoji;
            badge.innerHTML = `
                <span class="reaction-emoji">${emoji}</span>
                <span class="reaction-count">${data.count}</span>
            `;
            reactionsContainer.appendChild(badge);
        }
    },

    /**
     * Handle presence events.
     * Routes to presenceManager for processing.
     * 
     * @param {Object} event - Presence event
     */
    handlePresenceEvent(event) {
        if (typeof presenceManager !== 'undefined' && presenceManager.handlePresenceUpdate) {
            presenceManager.handlePresenceUpdate(event);
        } else {
            console.debug('[EventProcessor] PresenceManager not available, presence event ignored');
        }
    },

    /**
     * Remove oldest entries to maintain cache size limit.
     */
    evictOldest() {
        // Remove 10% of oldest entries
        const entriesToRemove = Math.max(1, Math.floor(this.MAX_CACHE_SIZE * 0.1));
        const entries = Array.from(this.processedEventIds.entries())
            .sort((a, b) => a[1] - b[1]); // Sort by timestamp (oldest first)

        for (let i = 0; i < entriesToRemove && i < entries.length; i++) {
            this.processedEventIds.delete(entries[i][0]);
        }

        console.debug(`[EventProcessor] Evicted ${entriesToRemove} oldest cache entries`);
    },

    /**
     * Clean up expired cache entries.
     */
    cleanupCache() {
        const now = Date.now();
        let removed = 0;

        for (const [eventId, timestamp] of this.processedEventIds.entries()) {
            if (now - timestamp > this.CACHE_TTL_MS) {
                this.processedEventIds.delete(eventId);
                removed++;
            }
        }

        if (removed > 0) {
            console.debug(`[EventProcessor] Cleaned up ${removed} expired cache entries`);
        }
    },

    /**
     * Simple hash function for fallback event ID generation.
     * 
     * @param {string} str - String to hash
     * @returns {string} - Hash string
     */
    simpleHash(str) {
        let hash = 0;
        for (let i = 0; i < str.length; i++) {
            const char = str.charCodeAt(i);
            hash = ((hash << 5) - hash) + char;
            hash = hash & hash; // Convert to 32bit integer
        }
        return Math.abs(hash).toString(36);
    },

    /**
     * Get current cache statistics (for debugging).
     * 
     * @returns {Object} - Cache stats
     */
    getStats() {
        return {
            cacheSize: this.processedEventIds.size,
            maxCacheSize: this.MAX_CACHE_SIZE,
            initialized: this.initialized
        };
    }
};

// Auto-initialize when script loads
document.addEventListener('DOMContentLoaded', () => {
    eventProcessor.init();
});
