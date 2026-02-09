/**
 * PresenceManager - User Presence (Online/Active) System
 * 
 * PURPOSE:
 * - Track user presence based on WebSocket connection AND tab visibility
 * - Send PRESENCE_ACTIVE / PRESENCE_INACTIVE events to server
 * - React to Page Visibility API changes
 * - Handle WebSocket connect/disconnect
 * 
 * PRESENCE RULE:
 * User is ACTIVE if and only if:
 *   - WebSocket is CONNECTED
 *   - AND document.visibilityState === "visible"
 * 
 * Otherwise: INACTIVE
 * 
 * INVARIANTS:
 * - NEVER infer presence from typing, messages, or timers
 * - ONLY react to WebSocket events and visibilitychange
 * - NO polling, NO heartbeats for presence
 * 
 * @since 1.0.0
 */
const presenceManager = {
    
    // State
    roomCode: null,
    userId: null,
    isConnected: false,     // WebSocket connection state
    isVisible: true,        // Browser tab visibility state
    lastSentState: null,    // Last presence state sent to server (to avoid duplicates)
    
    // Presence data from server
    presenceMap: {},        // userId -> isActive
    activeCount: 0,         // Number of active users in room
    
    // Callbacks
    onPresenceUpdate: null, // Callback when presence data changes
    
    /**
     * Initialize presence manager.
     * Must be called before any other method.
     * 
     * @param {string} roomCode - Room code
     * @param {string} userId - Current user ID
     * @param {Function} onPresenceUpdate - Callback when presence changes: (presenceMap, activeCount) => void
     */
    init(roomCode, userId, onPresenceUpdate) {
        this.roomCode = roomCode;
        this.userId = userId;
        this.onPresenceUpdate = onPresenceUpdate;
        
        // Initialize visibility state
        this.isVisible = this.getDocumentVisibility();
        
        // Listen to Page Visibility API
        this.setupVisibilityListener();
        
        console.log('[PresenceManager] Initialized:', { 
            roomCode, 
            userId, 
            isVisible: this.isVisible 
        });
    },
    
    /**
     * Setup visibility change listener.
     * Reacts to tab visibility changes (hidden/visible).
     */
    setupVisibilityListener() {
        document.addEventListener('visibilitychange', () => {
            const wasVisible = this.isVisible;
            this.isVisible = this.getDocumentVisibility();
            
            console.log('[PresenceManager] Visibility changed:', {
                from: wasVisible,
                to: this.isVisible,
                state: document.visibilityState
            });
            
            // Update presence based on new visibility
            this.evaluateAndSendPresence();
        });
    },
    
    /**
     * Get current document visibility state.
     * 
     * @returns {boolean} - true if visible, false if hidden
     */
    getDocumentVisibility() {
        return document.visibilityState === 'visible';
    },
    
    /**
     * Notify that WebSocket connection state changed.
     * Called by webSocketAdapter or transportFactory.
     * 
     * @param {boolean} connected - true if WebSocket connected
     */
    onConnectionChange(connected) {
        const wasConnected = this.isConnected;
        this.isConnected = connected;
        
        console.log('[PresenceManager] Connection changed:', {
            from: wasConnected,
            to: connected
        });
        
        // Update presence based on new connection state
        this.evaluateAndSendPresence();
    },
    
    /**
     * Evaluate presence state and send update to server if changed.
     * 
     * PRESENCE RULE:
     * - ACTIVE if: connected AND visible
     * - INACTIVE if: disconnected OR hidden
     */
    evaluateAndSendPresence() {
        // Calculate current presence state
        const isActive = this.isConnected && this.isVisible;
        const currentState = isActive ? 'ACTIVE' : 'INACTIVE';
        
        // Avoid sending duplicate states
        if (currentState === this.lastSentState) {
            console.debug('[PresenceManager] State unchanged, not sending:', currentState);
            return;
        }
        
        // Send presence update to server
        if (isActive) {
            this.sendPresenceActive();
        } else {
            this.sendPresenceInactive();
        }
        
        this.lastSentState = currentState;
        
        console.log('[PresenceManager] Presence evaluated and sent:', {
            connected: this.isConnected,
            visible: this.isVisible,
            state: currentState
        });
    },
    
    /**
     * Send PRESENCE_ACTIVE message to server.
     */
    sendPresenceActive() {
        if (!this.roomCode) {
            console.warn('[PresenceManager] Cannot send presence: roomCode not set');
            return;
        }
        
        try {
            // Send via WebSocket adapter
            if (typeof webSocketAdapter !== 'undefined' && webSocketAdapter.stompClient) {
                webSocketAdapter.stompClient.publish({
                    destination: '/app/presence/active',
                    body: JSON.stringify({
                        roomCode: this.roomCode
                    })
                });
                
                console.log('[PresenceManager] Sent PRESENCE_ACTIVE');
            } else {
                console.warn('[PresenceManager] Cannot send presence: WebSocket not available');
            }
        } catch (error) {
            console.error('[PresenceManager] Error sending PRESENCE_ACTIVE:', error);
        }
    },
    
    /**
     * Send PRESENCE_INACTIVE message to server.
     */
    sendPresenceInactive() {
        if (!this.roomCode) {
            console.warn('[PresenceManager] Cannot send presence: roomCode not set');
            return;
        }
        
        try {
            // Send via WebSocket adapter
            if (typeof webSocketAdapter !== 'undefined' && webSocketAdapter.stompClient) {
                webSocketAdapter.stompClient.publish({
                    destination: '/app/presence/inactive',
                    body: JSON.stringify({
                        roomCode: this.roomCode
                    })
                });
                
                console.log('[PresenceManager] Sent PRESENCE_INACTIVE');
            } else {
                console.warn('[PresenceManager] Cannot send presence: WebSocket not available');
            }
        } catch (error) {
            console.error('[PresenceManager] Error sending PRESENCE_INACTIVE:', error);
        }
    },
    
    /**
     * Handle presence update from server.
     * Called by eventProcessor when PRESENCE event is received.
     * 
     * @param {Object} event - Presence event from server
     * @param {Object} event.presenceMap - Map of userId -> isActive
     * @param {number} event.activeCount - Number of active users
     */
    handlePresenceUpdate(event) {
        if (!event || !event.presenceMap) {
            console.warn('[PresenceManager] Invalid presence event:', event);
            return;
        }
        
        this.presenceMap = event.presenceMap;
        this.activeCount = event.activeCount || 0;
        
        console.log('[PresenceManager] Presence updated:', {
            activeCount: this.activeCount,
            totalUsers: Object.keys(this.presenceMap).length,
            presenceMap: this.presenceMap
        });
        
        // Notify UI via callback
        if (typeof this.onPresenceUpdate === 'function') {
            this.onPresenceUpdate(this.presenceMap, this.activeCount);
        }
    },
    
    /**
     * Check if a specific user is active.
     * 
     * @param {string} userId - User ID to check
     * @returns {boolean} - true if user is active
     */
    isUserActive(userId) {
        return this.presenceMap[userId] === true;
    },
    
    /**
     * Get list of active user IDs.
     * 
     * @returns {string[]} - Array of active user IDs
     */
    getActiveUserIds() {
        return Object.keys(this.presenceMap).filter(uid => this.presenceMap[uid] === true);
    },
    
    /**
     * Get active user count.
     * 
     * @returns {number} - Number of active users
     */
    getActiveCount() {
        return this.activeCount;
    }
};
