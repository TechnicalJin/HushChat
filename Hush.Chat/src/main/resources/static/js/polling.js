// Message Polling Logic

const polling = {
    roomCode: null,
    userId: null,
    lastMessageTime: null,
    pollInterval: null,
    isPolling: false,
    pollTimeoutMs: 2000, // 2 seconds for short polling
    longPollTimeoutSec: 20, // 20 seconds for long polling
    useLongPolling: true, // Use long polling by default
    retryCount: 0,
    maxRetries: 3,
    
    /**
     * Initialize polling module
     */
    init() {
        try {
            this.roomCode = localStorage.getItem('roomCode');
            this.userId = localStorage.getItem('userId');
        } catch (e) {
            console.error('Failed to access localStorage for polling');
            return;
        }
        
        // Get stored last message time or use join time
        let storedTime, joinTime;
        try {
            storedTime = localStorage.getItem('lastMessageTime');
            joinTime = localStorage.getItem('joinTime');
        } catch (e) {
            // Ignore localStorage errors
        }
        
        if (storedTime) {
            this.lastMessageTime = storedTime;
        } else if (joinTime) {
            this.lastMessageTime = joinTime;
        } else {
            // Default to current time (no history on refresh)
            this.lastMessageTime = new Date().toISOString();
        }
        
        if (!this.roomCode || !this.userId) {
            console.error('Missing room or user info for polling');
            return;
        }
        
        console.log('Polling initialized for room:', this.roomCode);
        this.startPolling();
        
        // Stop polling when page is hidden
        document.addEventListener('visibilitychange', () => {
            if (document.hidden) {
                this.stopPolling();
            } else {
                this.startPolling();
            }
        });
        
        // Stop polling on page unload
        window.addEventListener('beforeunload', () => {
            this.stopPolling();
        });
    },
    
    /**
     * Start polling for new messages
     */
    startPolling() {
        if (this.isPolling) {
            return;
        }
        
        this.isPolling = true;
        this.retryCount = 0;
        console.log('Starting message polling...');
        
        if (this.useLongPolling) {
            this.longPoll();
        } else {
            this.shortPoll();
        }
    },
    
    /**
     * Stop polling
     */
    stopPolling() {
        if (!this.isPolling && !this.pollInterval) {
            return; // Already stopped
        }
        
        this.isPolling = false;
        
        if (this.pollInterval) {
            try {
                clearTimeout(this.pollInterval);
            } catch (e) {
                // Silently ignore timeout clear errors
            }
            this.pollInterval = null;
        }
        
        console.log('Polling stopped');
    },
    
    /**
     * Short polling - poll every 2 seconds
     */
    async shortPoll() {
        if (!this.isPolling) {
            return;
        }
        
        try {
            await this.fetchMessages();
            this.retryCount = 0;
        } catch (error) {
            this.handlePollError(error);
        }
        
        // Schedule next poll
        if (this.isPolling) {
            this.pollInterval = setTimeout(() => {
                this.shortPoll();
            }, this.pollTimeoutMs);
        }
    },
    
    /**
     * Long polling - wait for server response up to 20 seconds
     */
    async longPoll() {
        if (!this.isPolling) {
            return;
        }
        
        try {
            const messages = await this.fetchMessagesLong();
            this.retryCount = 0;
            
            // Immediately start next long poll
            if (this.isPolling) {
                // Small delay to prevent overwhelming the server
                this.pollInterval = setTimeout(() => {
                    this.longPoll();
                }, 100);
            }
        } catch (error) {
            this.handlePollError(error);
            
            // On error, wait before retrying
            if (this.isPolling) {
                const backoff = Math.min(1000 * Math.pow(2, this.retryCount), 10000);
                this.pollInterval = setTimeout(() => {
                    this.longPoll();
                }, backoff);
            }
        }
    },
    
    /**
     * Fetch messages using short polling endpoint
     */
    async fetchMessages() {
        const params = new URLSearchParams();
        params.append('userId', this.userId);
        
        if (this.lastMessageTime) {
            params.append('since', this.formatTimestamp(this.lastMessageTime));
        }
        
        const response = await api.get(
            `/messages/${this.roomCode}?${params.toString()}`,
            false  // No auth required
        );
        
        if (response.success && response.data) {
            // Process unified events (messages + reactions)
            this.processEvents(response.data.events || []);
            
            // Backward compatibility: also process messages array
            if (!response.data.events && response.data.messages) {
                this.processMessages(response.data.messages || []);
            }
            
            // Update last message time from server
            if (response.data.serverTime) {
                this.updateLastMessageTime(response.data.serverTime);
            }
        }
        
        return response.data?.messages || [];
    },
    
    /**
     * Fetch messages using long polling endpoint
     */
    async fetchMessagesLong() {
        const params = new URLSearchParams();
        params.append('userId', this.userId);
        params.append('timeout', this.longPollTimeoutSec.toString());
        
        if (this.lastMessageTime) {
            params.append('since', this.formatTimestamp(this.lastMessageTime));
        }
        
        const response = await api.get(
            `/messages/${this.roomCode}/poll?${params.toString()}`,
            false  // No auth required
        );
        
        if (response.success && response.data) {
            // Process unified events (messages + reactions)
            this.processEvents(response.data.events || []);
            
            // Backward compatibility: also process messages array
            if (!response.data.events && response.data.messages) {
                this.processMessages(response.data.messages || []);
            }
            
            // Update last message time from server
            if (response.data.serverTime) {
                this.updateLastMessageTime(response.data.serverTime);
            }
        }
        
        return response.data?.messages || [];
    },
    
    /**
     * Process unified events from poll response.
     * Handles both MESSAGE and REACTION event types.
     */
    processEvents(events) {
        if (!events || events.length === 0) {
            return;
        }
        
        console.log(`Received ${events.length} event(s) from poll`);
        
        const messages = [];
        const reactionEvents = [];
        
        // Separate events by type
        for (const event of events) {
            switch (event.type) {
                case 'MESSAGE':
                case 'MESSAGE_EDIT':
                case 'MESSAGE_DELETE':
                    if (event.message) {
                        messages.push(event.message);
                    }
                    break;
                case 'REACTION':
                    reactionEvents.push(event);
                    break;
                default:
                    console.warn('Unknown event type:', event.type);
            }
        }
        
        // Process messages
        if (messages.length > 0) {
            this.processMessages(messages);
        }
        
        // Process reaction events
        if (reactionEvents.length > 0) {
            this.processReactionEvents(reactionEvents);
        }
    },
    
    /**
     * Process reaction events from poll response.
     * Updates the UI with reaction changes from other users.
     */
    processReactionEvents(reactionEvents) {
        if (!reactionEvents || reactionEvents.length === 0) {
            return;
        }
        
        console.log(`Processing ${reactionEvents.length} reaction event(s)`);
        
        for (const event of reactionEvents) {
            const { messageId, emoji, action, updatedReactionCounts, reactedByUserId, reactedByUserName } = event;
            
            if (!messageId) {
                console.warn('Reaction event missing messageId:', event);
                continue;
            }
            
            console.log(`Reaction ${action}: ${emoji} on message ${messageId} by ${reactedByUserName}`);
            
            // Update the reaction UI for this message
            if (typeof emojiSystem !== 'undefined') {
                if (updatedReactionCounts) {
                    // Full reaction update with all counts
                    this.updateMessageReactionsFromPoll(messageId, updatedReactionCounts);
                } else {
                    // Fallback: update single reaction
                    emojiSystem.updateMessageReactions(messageId, emoji, action, reactedByUserId);
                }
            }
        }
    },
    
    /**
     * Update message reactions from poll event with full reaction counts.
     * This ensures consistent state across all clients.
     */
    updateMessageReactionsFromPoll(messageId, reactionCounts) {
        const currentUserId = localStorage.getItem('userId');
        
        const messageDiv = document.querySelector(`[data-message-id="${messageId}"]`);
        if (!messageDiv) {
            console.warn('Message element not found for reaction update:', messageId);
            return;
        }
        
        // Use emojiSystem to render the full reaction state
        if (typeof emojiSystem !== 'undefined' && emojiSystem.renderMessageReactions) {
            emojiSystem.renderMessageReactions(messageDiv, reactionCounts, currentUserId);
        }
    },
    
    /**
     * Process received messages
     */
    processMessages(messages) {
        if (!messages || messages.length === 0) {
            return;
        }
        
        console.log(`Received ${messages.length} new message(s)`);
        
        // Render messages (chat module handles deduplication)
        if (typeof chat !== 'undefined' && chat.renderMessages) {
            chat.renderMessages(messages);
        }
        
        // Fetch reactions for the new messages (still needed for initial load)
        const messageIds = messages.map(m => m.messageId).filter(id => id);
        if (messageIds.length > 0 && typeof emojiSystem !== 'undefined') {
            emojiSystem.fetchAndRenderReactions(messageIds);
        }
        
        // Update last message time to the latest message timestamp
        const latestMessage = messages[messages.length - 1];
        if (latestMessage && latestMessage.timestamp) {
            this.updateLastMessageTime(latestMessage.timestamp);
        }
    },
    
    /**
     * Update last message time
     */
    updateLastMessageTime(timestamp) {
        this.lastMessageTime = timestamp;
        localStorage.setItem('lastMessageTime', timestamp);
    },
    
    /**
     * Format timestamp for API request
     */
    formatTimestamp(timestamp) {
        // Handle various timestamp formats
        if (!timestamp) {
            return '';
        }
        
        try {
            // If it's already in ISO format without milliseconds, use it
            if (timestamp.match(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/)) {
                return timestamp;
            }
            
            // Parse and format to ISO without timezone
            const date = new Date(timestamp);
            if (isNaN(date.getTime())) {
                return timestamp;
            }
            
            // Format as: YYYY-MM-DDTHH:MM:SS (LocalDateTime format)
            return date.getFullYear() + '-' +
                String(date.getMonth() + 1).padStart(2, '0') + '-' +
                String(date.getDate()).padStart(2, '0') + 'T' +
                String(date.getHours()).padStart(2, '0') + ':' +
                String(date.getMinutes()).padStart(2, '0') + ':' +
                String(date.getSeconds()).padStart(2, '0');
        } catch (e) {
            console.warn('Failed to format timestamp:', timestamp);
            return timestamp;
        }
    },
    
    /**
     * Handle polling errors
     */
    handlePollError(error) {
        // Guard against null/undefined errors
        if (!error) {
            return;
        }
        
        const errorMessage = error.message || '';
        
        // Timeout errors are expected for long polling - don't log as error
        const isTimeout = error.name === 'TimeoutError' || 
                          errorMessage.includes('timed out') ||
                          errorMessage.includes('timeout');
        
        if (!isTimeout) {
            console.warn('Polling issue:', errorMessage || error);
        }
        
        this.retryCount++;
        
        // Check for room-related errors (room expired, closed, or not found)
        if (errorMessage && (
            errorMessage.includes('Room') || 
            errorMessage.includes('not found') ||
            errorMessage.includes('closed') ||
            errorMessage.includes('expired') ||
            errorMessage.includes('not a member') ||
            errorMessage.includes('has been closed')
        )) {
            // Room is gone or user was removed
            console.log('Room is no longer available, stopping polling');
            this.stopPolling();
            
            if (typeof chat !== 'undefined' && chat && typeof chat.handleRoomClosed === 'function') {
                try {
                    chat.handleRoomClosed();
                } catch (e) {
                    // Silently ignore room closed handler errors
                }
            }
            return;
        }
        
        // Check for auth errors
        if (errorMessage && (
            errorMessage.includes('Unauthorized') || 
            errorMessage.includes('Session')
        )) {
            // Session expired
            this.stopPolling();
            return;
        }
        
        // For timeout errors, don't count as retry - it's normal
        if (isTimeout) {
            this.retryCount = 0;
            return;
        }
        
        if (this.retryCount > this.maxRetries) {
            console.warn('Max polling retries exceeded, falling back to short polling');
            this.useLongPolling = false;
            this.retryCount = 0;
        }
    },
    
    /**
     * Switch between long and short polling
     */
    setPollingMode(useLongPolling) {
        this.useLongPolling = useLongPolling;
        
        // Restart polling with new mode
        this.stopPolling();
        this.startPolling();
    }
};

// Initialize when DOM is ready
document.addEventListener('DOMContentLoaded', () => {
    // Small delay to ensure chat module is initialized first
    setTimeout(() => {
        polling.init();
    }, 100);
});
