/**
 * PollingService - HTTP Long Polling Fallback
 * 
 * PURPOSE:
 * - Fetch messages via HTTP GET when WebSocket is unavailable
 * - Verify message delivery
 * - Handle message history loading on connection
 * 
 * @since 1.0.0 (Restored)
 */
const pollingService = {

    // Configuration
    POLL_INTERVAL: 3000, // 3 seconds
    MAX_ERRORS: 5,

    // State
    active: false,
    intervalId: null,
    errorCount: 0,
    roomCode: null,
    userId: null,
    lastMessageTime: null,

    /**
     * Initialize polling service
     */
    init(roomCode) {
        this.roomCode = roomCode;
        this.userId = localStorage.getItem('userId');
        this.lastMessageTime = null; // Always fetch fresh history on init/switch

        console.log('[Polling] Initialized for room:', roomCode);
    },

    /**
     * Start polling loop
     */
    start() {
        if (this.active) return;

        this.active = true;
        this.errorCount = 0;

        console.log('[Polling] Starting polling loop...');

        // Initial fetch immediately
        this.poll();

        // Start interval
        this.intervalId = setInterval(() => {
            this.poll();
        }, this.POLL_INTERVAL);
    },

    /**
     * Stop polling loop
     */
    stop() {
        this.active = false;
        if (this.intervalId) {
            clearInterval(this.intervalId);
            this.intervalId = null;
        }
        console.log('[Polling] Stopped');
    },

    /**
     * Poll for new messages
     */
    async poll() {
        if (!this.active || !this.roomCode) return;

        try {
            // Build query params
            let endpoint = `/messages/${this.roomCode}?userId=${this.userId}`;
            if (this.lastMessageTime) {
                endpoint += `&since=${this.lastMessageTime}`;
            }

            const response = await api.get(endpoint);

            if (response.success && response.data) {
                // Reset errors on success
                this.errorCount = 0;

                // Update server time if provided
                if (response.data.serverTime) {
                    // Sync time if needed
                }

                // Process messages
                const messages = response.data.messages || [];
                if (messages.length > 0) {
                    this.processMessages(messages);
                }
            }
        } catch (error) {
            this.errorCount++;
            console.warn(`[Polling] Error (${this.errorCount}/${this.MAX_ERRORS}):`, error);

            // Check if room is closed/invalid
            if (error.message && (error.message.includes('not found') || error.message.includes('closed'))) {
                this.stop();
                if (typeof chat !== 'undefined' && chat.handleRoomClosed) {
                    chat.handleRoomClosed();
                }
                return;
            }

            // Backoff if too many errors
            if (this.errorCount >= this.MAX_ERRORS) {
                console.error('[Polling] Too many errors, stopping temporarily');
                this.stop();
                // Try to restart after 30s
                setTimeout(() => this.start(), 30000);
            }
        }
    },

    /**
     * Process received messages and route to EventProcessor
     */
    processMessages(messages) {
        if (!messages || messages.length === 0) return;

        // Sort by timestamp to ensure correct order
        messages.sort((a, b) => new Date(a.timestamp) - new Date(b.timestamp));

        // Update last message time from the LATEST message
        const lastMsg = messages[messages.length - 1];
        if (lastMsg.timestamp) {
            this.lastMessageTime = lastMsg.timestamp;
            localStorage.setItem('lastMessageTime', this.lastMessageTime);
        }

        // Convert DTOs to Events and route
        const events = messages.map(msg => this.wrapMessageAsEvent(msg));

        if (typeof eventProcessor !== 'undefined') {
            eventProcessor.processEvents(events);
        }

        // FIX: Fetch reactions for these messages (since DTO doesn't include them)
        // This ensures reactions appear even in polling mode
        if (typeof emojiSystem !== 'undefined' && emojiSystem.fetchAndRenderReactions) {
            const messageIds = messages.map(m => m.messageId);
            emojiSystem.fetchAndRenderReactions(messageIds);
        }
    },

    /**
     * Wrap message DTO into an Event object (for compatibility with EventProcessor)
     */
    wrapMessageAsEvent(msg) {
        // Determine event type
        let type = 'MESSAGE';
        if (msg.deleted) type = 'MESSAGE_DELETE';
        else if (msg.edited) type = 'MESSAGE_EDIT';

        // Construct event
        return {
            type: type,
            messageId: msg.messageId,
            timestamp: msg.timestamp,
            message: msg
        };
    }
};
