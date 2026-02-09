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
    activeTransport: null, // 'websocket' | 'polling' | null
    roomCode: null,
    token: null,
    initialized: false,

    /**
     * Initialize transport (WebSocket with Polling fallback)
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

        // Initial strategy: Try WebSocket first, then Polling
        if (config.WEBSOCKET.ENABLED) {
            try {
                const wsConnected = await this.initWebSocket();
                if (wsConnected) {
                    this.activeTransport = 'websocket';
                    this.initialized = true;
                    return 'websocket';
                }
            } catch (e) {
                console.warn('[Transport] WebSocket init failed, falling back to polling:', e);
            }
        }

        // Fallback to Polling
        if (config.POLLING.ENABLED) {
            this.initPolling();
            this.activeTransport = 'polling';
            this.initialized = true;
            return 'polling';
        }

        this.showConnectionError('No connection method available.');
        throw new Error('No transport available');
    },

    /**
     * Initialize WebSocket
     */
    async initWebSocket() {
        if (typeof webSocketAdapter === 'undefined') return false;

        console.log('[Transport] Connecting via WebSocket...');
        return await webSocketAdapter.init(
            this.roomCode,
            this.token,
            (reason) => this.onWebSocketDisconnect(reason)
        );
    },

    /**
     * Initialize Polling
     */
    initPolling() {
        if (typeof pollingService === 'undefined') {
            console.warn('[Transport] Polling service not found');
            return;
        }

        console.log('[Transport] Starting polling fallback...');
        pollingService.init(this.roomCode);
        pollingService.start();

        // Hide error if we successfully started polling
        this.hideConnectionError();
    },

    /**
     * Handle WebSocket disconnect
     */
    onWebSocketDisconnect(reason) {
        console.warn('[Transport] WebSocket disconnected:', reason);

        // If WebSocket dies, switch to polling immediately
        if (this.activeTransport === 'websocket') {
            if (config.POLLING.ENABLED) {
                this.initPolling();
                this.activeTransport = 'polling';
                console.log('[Transport] switched to polling due to WS disconnect');
            } else {
                this.showConnectionError('Connection lost. Reconnecting...');
            }
        }
    },

    /**
     * Show connection error to user.
     */
    showConnectionError(message) {
        const errorContainer = document.getElementById('connection-error') ||
            document.getElementById('error-message');
        if (errorContainer) {
            errorContainer.textContent = message;
            errorContainer.style.display = 'block';
        }
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
     */
    getStoredToken() {
        try {
            let token = localStorage.getItem('sessionToken');
            if (token) return token;
            return localStorage.getItem(config?.STORAGE_KEYS?.SESSION_TOKEN || 'sessionToken');
        } catch (e) {
            console.warn('[Transport] Failed to access localStorage for token');
            return null;
        }
    },

    /**
     * Cleanup and disconnect
     */
    cleanup() {
        console.log('[Transport] Cleaning up...');

        if (typeof webSocketAdapter !== 'undefined') {
            webSocketAdapter.disconnect();
        }

        if (typeof pollingService !== 'undefined') {
            pollingService.stop();
        }

        this.activeTransport = null;
        this.initialized = false;
    },

    getStatus() {
        return {
            activeTransport: this.activeTransport,
            initialized: this.initialized,
            roomCode: this.roomCode
        };
    }
};

// Cleanup on page unload
window.addEventListener('beforeunload', () => {
    transportFactory.cleanup();
});
