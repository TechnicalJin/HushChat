/**
 * TransportFactory - WebSocket-Only Transport Management
 * 
 * PURPOSE:
 * - Connect via WebSocket (STOMP + SockJS)
 * - Route ALL events through EventProcessor
 * - Show error if WebSocket fails (NO polling fallback)
 * 
 * NOTE: Long polling support intentionally removed.
 * WebSocket is the ONLY real-time transport mechanism.
 * 
 * @since 2.0.0 (WebSocket-only version)
 */
const transportFactory = {
    
    // State
    activeTransport: null, // 'websocket' | null
    roomCode: null,
    token: null,
    initialized: false,
    
    /**
     * Initialize WebSocket transport for a room.
     * 
     * @param {string} roomCode - Room code to join
     * @param {string} token - JWT token (required for WebSocket auth)
     * @returns {Promise<string>} - Active transport type ('websocket')
     * @throws {Error} - If WebSocket connection fails
     */
    async init(roomCode, token = null) {
        if (this.initialized && this.roomCode === roomCode) {
            console.log('[Transport] Already initialized for room:', roomCode);
            return this.activeTransport;
        }
        
        this.roomCode = roomCode;
        this.token = token || this.getStoredToken();
        
        // Initialize EventProcessor first
        if (typeof eventProcessor !== 'undefined') {
            eventProcessor.init();
        }
        
        // Validate WebSocket requirements
        const validationError = this.validateWebSocketRequirements();
        if (validationError) {
            console.error('[Transport] WebSocket requirements not met:', validationError);
            this.showConnectionError(validationError);
            throw new Error(validationError);
        }
        
        console.log('[Transport] Connecting via WebSocket...');
        const wsConnected = await this.tryWebSocket();
        
        if (wsConnected) {
            this.activeTransport = 'websocket';
            this.initialized = true;
            console.log('[Transport] WebSocket connected successfully');
            return 'websocket';
        }
        
        // WebSocket failed - show error (no fallback)
        const errorMsg = 'WebSocket connection failed. Please refresh the page and try again.';
        console.error('[Transport]', errorMsg);
        this.showConnectionError(errorMsg);
        throw new Error(errorMsg);
    },
    
    /**
     * Validate that all WebSocket requirements are met.
     * 
     * @returns {string|null} - Error message if requirements not met, null if OK
     */
    validateWebSocketRequirements() {
        // Check if WebSocket adapter is available
        if (typeof webSocketAdapter === 'undefined') {
            return 'WebSocket adapter not loaded';
        }
        
        // Check if SockJS is loaded
        if (typeof SockJS === 'undefined') {
            return 'SockJS library not loaded';
        }
        
        // Check if token is available
        if (!this.token) {
            return 'Authentication token not available. Please rejoin the room.';
        }
        
        return null; // All requirements met
    },
    
    /**
     * Try to establish WebSocket connection.
     * 
     * @returns {Promise<boolean>} - true if connected
     */
    async tryWebSocket() {
        try {
            const connected = await webSocketAdapter.init(
                this.roomCode,
                this.token,
                (reason) => this.onWebSocketDisconnect(reason)
            );
            
            return connected;
            
        } catch (error) {
            console.error('[Transport] WebSocket initialization failed:', error);
            return false;
        }
    },
    
    /**
     * Handle WebSocket disconnect.
     * Shows error to user and attempts reconnection.
     * 
     * @param {string} reason - Reason for disconnect
     */
    onWebSocketDisconnect(reason) {
        console.warn('[Transport] WebSocket disconnected:', reason);
        
        if (this.activeTransport === 'websocket') {
            this.activeTransport = null;
            this.showConnectionError('Connection lost. Attempting to reconnect...');
            
            // Attempt reconnection after a delay
            setTimeout(() => {
                this.attemptReconnect();
            }, 3000);
        }
    },
    
    /**
     * Attempt to reconnect WebSocket.
     */
    async attemptReconnect() {
        console.log('[Transport] Attempting reconnection...');
        
        try {
            const connected = await this.tryWebSocket();
            if (connected) {
                this.activeTransport = 'websocket';
                this.hideConnectionError();
                console.log('[Transport] Reconnected successfully');
            } else {
                this.showConnectionError('Reconnection failed. Please refresh the page.');
            }
        } catch (error) {
            console.error('[Transport] Reconnection failed:', error);
            this.showConnectionError('Reconnection failed. Please refresh the page.');
        }
    },
    
    /**
     * Show connection error to user.
     * 
     * @param {string} message - Error message
     */
    showConnectionError(message) {
        // Try to show error in UI
        const errorContainer = document.getElementById('connection-error') || 
                               document.getElementById('error-message');
        if (errorContainer) {
            errorContainer.textContent = message;
            errorContainer.style.display = 'block';
        }
        
        // Also log to console
        console.error('[Transport] Connection error:', message);
    },
    
    /**
     * Hide connection error.
     */
    hideConnectionError() {
        const errorContainer = document.getElementById('connection-error') || 
                               document.getElementById('error-message');
        if (errorContainer) {
            errorContainer.style.display = 'none';
        }
    },
    
    /**
     * Get stored JWT token.
     * 
     * @returns {string|null} - JWT token
     */
    getStoredToken() {
        try {
            // Try sessionToken first (main auth token)
            let token = localStorage.getItem('sessionToken');
            if (token) return token;
            
            // Try other possible storage keys
            token = localStorage.getItem(config?.STORAGE_KEYS?.SESSION_TOKEN || 'sessionToken');
            return token;
            
        } catch (e) {
            console.warn('[Transport] Failed to access localStorage for token');
            return null;
        }
    },
    
    /**
     * Cleanup and disconnect WebSocket.
     */
    cleanup() {
        console.log('[Transport] Cleaning up...');
        
        // Disconnect WebSocket
        if (typeof webSocketAdapter !== 'undefined') {
            webSocketAdapter.disconnect();
        }
        
        this.activeTransport = null;
        this.initialized = false;
    },
    
    /**
     * Get current transport status.
     * 
     * @returns {Object} - Status object
     */
    getStatus() {
        return {
            activeTransport: this.activeTransport,
            initialized: this.initialized,
            roomCode: this.roomCode,
            hasToken: !!this.token,
            webSocketStatus: typeof webSocketAdapter !== 'undefined' 
                ? webSocketAdapter.getStatus() 
                : null,
            eventProcessorStats: typeof eventProcessor !== 'undefined'
                ? eventProcessor.getStats()
                : null
        };
    },
    
    /**
     * Force WebSocket reconnection (for debugging).
     */
    async forceReconnect() {
        console.log('[Transport] Force reconnecting WebSocket...');
        
        if (typeof webSocketAdapter !== 'undefined') {
            webSocketAdapter.disconnect();
        }
        
        this.activeTransport = null;
        this.initialized = false;
        
        try {
            await this.init(this.roomCode, this.token);
        } catch (error) {
            console.error('[Transport] Force reconnect failed:', error);
        }
    }
};

// Cleanup on page unload
window.addEventListener('beforeunload', () => {
    transportFactory.cleanup();
});
