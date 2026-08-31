/**
 * WebSocketAdapter - STOMP over SockJS WebSocket connection
 * 
 * PURPOSE:
 * - Establish WebSocket connection with JWT authentication
 * - Subscribe to room events via STOMP
 * - Route received events to EventProcessor
 * - Handle reconnection attempts with backoff
 * - Trigger disconnect callback on failure
 * 
 * NOTE: Long polling support intentionally removed. WebSocket is the ONLY transport.
 * 
 * INVARIANTS:
 * - NEVER directly updates UI (routes through EventProcessor)
 * - Silently fails without breaking application
 * - All errors caught and logged
 * 
 * DEPENDENCIES:
 * - SockJS: https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js
 * - STOMP.js: https://cdn.jsdelivr.net/npm/@stomp/stompjs@7/bundles/stomp.umd.min.js
 * 
 * @since 2.0.0 (WebSocket-only version)
 */
const webSocketAdapter = {
    
    // Configuration
    WEBSOCKET_ENDPOINT: '/ws',
    RECONNECT_ATTEMPTS: 3,
    RECONNECT_DELAYS: [1000, 3000, 5000], // Exponential backoff
    HEARTBEAT_INCOMING: 10000, // 10 seconds
    HEARTBEAT_OUTGOING: 10000,
    
    // State
    stompClient: null,
    connected: false,
    connecting: false,
    roomCode: null,
    token: null,
    subscription: null,
    reconnectAttempts: 0,
    fallbackCallback: null,
    
    /**
     * Initialize WebSocket connection.
     * 
     * @param {string} roomCode - Room code to subscribe to
     * @param {string} token - JWT token for authentication
     * @param {Function} onFallback - Callback to trigger when fallback is needed
     * @returns {Promise<boolean>} - true if connected successfully
     */
    async init(roomCode, token, onFallback) {
        // Check for required libraries
        if (typeof SockJS === 'undefined') {
            console.warn('[WebSocket] SockJS library not loaded');
            return false;
        }
        
        if (typeof StompJs === 'undefined' && typeof Stomp === 'undefined') {
            console.warn('[WebSocket] STOMP library not loaded');
            return false;
        }
        
        this.roomCode = roomCode;
        this.token = token;
        this.fallbackCallback = onFallback;
        this.reconnectAttempts = 0;
        
        return this.connect();
    },
    
    /**
     * Establish WebSocket connection.
     * 
     * @returns {Promise<boolean>} - true if connected
     */
    connect() {
        return new Promise((resolve) => {
            if (this.connected || this.connecting) {
                console.debug('[WebSocket] Already connected or connecting');
                resolve(this.connected);
                return;
            }
            
            this.connecting = true;
            
            try {
                // Build WebSocket URL with JWT token
                const wsUrl = this.buildWebSocketUrl();
                console.log('[WebSocket] Connecting to:', wsUrl);
                
                // Create SockJS connection
                const socket = new SockJS(wsUrl);
                
                // Create STOMP client
                // Support both StompJs (v7) and Stomp (legacy)
                if (typeof StompJs !== 'undefined') {
                    this.stompClient = new StompJs.Client({
                        webSocketFactory: () => socket,
                        reconnectDelay: 0, // We handle reconnection manually
                        heartbeatIncoming: this.HEARTBEAT_INCOMING,
                        heartbeatOutgoing: this.HEARTBEAT_OUTGOING,
                        debug: (str) => {
                            if (str.includes('ERROR') || str.includes('DISCONNECT')) {
                                console.warn('[WebSocket] STOMP:', str);
                            }
                        }
                    });
                    
                    this.stompClient.onConnect = (frame) => {
                        this.onConnected(frame);
                        resolve(true);
                    };
                    
                    this.stompClient.onStompError = (frame) => {
                        this.onError(frame);
                        resolve(false);
                    };
                    
                    this.stompClient.onWebSocketClose = (event) => {
                        this.onDisconnect(event);
                    };
                    
                    this.stompClient.activate();
                    
                } else if (typeof Stomp !== 'undefined') {
                    // Legacy Stomp.js
                    this.stompClient = Stomp.over(socket);
                    this.stompClient.debug = null; // Disable verbose logging
                    
                    this.stompClient.connect({}, 
                        (frame) => {
                            this.onConnected(frame);
                            resolve(true);
                        },
                        (error) => {
                            this.onError(error);
                            resolve(false);
                        }
                    );
                } else {
                    console.error('[WebSocket] No STOMP library available');
                    this.connecting = false;
                    resolve(false);
                }
                
            } catch (error) {
                console.error('[WebSocket] Connection error:', error);
                this.connecting = false;
                this.triggerFallback('Connection error');
                resolve(false);
            }
        });
    },
    
    /**
     * Build WebSocket URL with JWT token.
     * 
     * @returns {string} - Full WebSocket URL
     */
    buildWebSocketUrl() {
        const baseUrl = window.location.origin;
        const endpoint = this.WEBSOCKET_ENDPOINT;
        
        // Append JWT token as query parameter (required by backend)
        return `${baseUrl}${endpoint}?token=${encodeURIComponent(this.token)}`;
    },
    
    /**
     * Handle successful connection.
     * 
     * @param {Object} frame - STOMP CONNECTED frame
     */
    onConnected(frame) {
        console.log('[WebSocket] Connected successfully');
        this.connected = true;
        this.connecting = false;
        this.reconnectAttempts = 0;
        
        // Subscribe to room events
        this.subscribeToRoom();

        if (typeof chat !== 'undefined' && typeof chat.loadActiveRoomMessages === 'function') {
            chat.loadActiveRoomMessages();
        }
        
        // Notify presence manager of connection
        if (typeof presenceManager !== 'undefined' && presenceManager.onConnectionChange) {
            presenceManager.onConnectionChange(true);
        }
    },
    
    /**
     * Subscribe to room-specific event queue.
     * 
     * NOTE: Backend sends to /user/{userId}/queue/room/{roomCode}
     * Client subscribes to /user/queue/room/{roomCode} (STOMP resolves /user prefix)
     */
    subscribeToRoom() {
        if (!this.stompClient || !this.connected) {
            console.warn('[WebSocket] Cannot subscribe: not connected');
            return;
        }
        
        const destination = `/user/queue/room/${this.roomCode}`;
        console.log('[WebSocket] Subscribing to:', destination);
        
        try {
            // Support both StompJs (v7) and legacy Stomp
            if (typeof this.stompClient.subscribe === 'function') {
                this.subscription = this.stompClient.subscribe(destination, (message) => {
                    this.onMessage(message);
                });
            } else {
                console.error('[WebSocket] STOMP client does not support subscribe');
            }
            
            console.log('[WebSocket] Subscribed to room:', this.roomCode);
            
        } catch (error) {
            console.error('[WebSocket] Subscription error:', error);
            this.triggerFallback('Subscription failed');
        }
    },
    
    /**
     * Handle received message.
     * Routes to EventProcessor - does NOT update UI directly.
     * 
     * @param {Object} message - STOMP message
     */
    onMessage(message) {
        try {
            const body = message.body;
            const event = JSON.parse(body);
            
            // DIAGNOSTIC LOG: WS message received
            const messageId = event.messageId || event.message?.messageId;
            // console.log(`🔵 WS MESSAGE RECEIVED: ${messageId} | Type: ${event.type || 'MESSAGE'}`);
            
            console.debug('[WebSocket] Received event:', event.type || 'MESSAGE');
            
            // Route to EventProcessor for deduplication and UI update
            if (typeof eventProcessor !== 'undefined') {
                eventProcessor.processEvent(event);
            } else {
                console.warn('[WebSocket] EventProcessor not available');
            }
            
        } catch (error) {
            console.error('[WebSocket] Failed to process message:', error);
        }
    },
    
    /**
     * Handle connection error.
     * 
     * @param {Object} error - Error object or STOMP frame
     */
    onError(error) {
        console.error('[WebSocket] Connection error:', error);
        this.connected = false;
        this.connecting = false;
        
        // Notify presence manager of disconnection
        if (typeof presenceManager !== 'undefined' && presenceManager.onConnectionChange) {
            presenceManager.onConnectionChange(false);
        }
        
        // Attempt reconnection
        this.attemptReconnect();
    },
    
    /**
     * Handle disconnection.
     * 
     * @param {Object} event - Close event
     */
    onDisconnect(event) {
        console.warn('[WebSocket] Disconnected:', event?.reason || 'Unknown reason');
        this.connected = false;
        this.connecting = false;
        
        // Notify presence manager of disconnection
        if (typeof presenceManager !== 'undefined' && presenceManager.onConnectionChange) {
            presenceManager.onConnectionChange(false);
        }
        
        // Clear subscription
        if (this.subscription) {
            try {
                this.subscription.unsubscribe();
            } catch (e) {
                // Ignore unsubscribe errors
            }
            this.subscription = null;
        }
        
        // Attempt reconnection
        this.attemptReconnect();
    },
    
    /**
     * Attempt to reconnect with exponential backoff.
     */
    attemptReconnect() {
        if (this.reconnectAttempts >= this.RECONNECT_ATTEMPTS) {
            console.log('[WebSocket] Max reconnect attempts reached, triggering fallback');
            this.triggerFallback('Max reconnection attempts exceeded');
            return;
        }
        
        const delay = this.RECONNECT_DELAYS[this.reconnectAttempts] || 5000;
        this.reconnectAttempts++;
        
        console.log(`[WebSocket] Reconnecting in ${delay}ms (attempt ${this.reconnectAttempts}/${this.RECONNECT_ATTEMPTS})`);
        
        setTimeout(() => {
            if (!this.connected && !this.connecting) {
                this.connect();
            }
        }, delay);
    },
    
    /**
     * Trigger disconnect callback.
     * Notifies transportFactory of connection failure.
     * 
     * @param {string} reason - Reason for disconnect
     */
    triggerFallback(reason) {
        console.log('[WebSocket] Triggering disconnect callback:', reason);
        
        if (typeof this.fallbackCallback === 'function') {
            this.fallbackCallback(reason);
        }
    },
    
    /**
     * Disconnect and cleanup.
     */
    disconnect() {
        console.log('[WebSocket] Disconnecting...');
        
        if (this.subscription) {
            try {
                this.subscription.unsubscribe();
            } catch (e) {
                // Ignore
            }
            this.subscription = null;
        }
        
        if (this.stompClient) {
            try {
                if (typeof this.stompClient.deactivate === 'function') {
                    this.stompClient.deactivate();
                } else if (typeof this.stompClient.disconnect === 'function') {
                    this.stompClient.disconnect();
                }
            } catch (e) {
                // Ignore disconnect errors
            }
            this.stompClient = null;
        }
        
        this.connected = false;
        this.connecting = false;
        this.reconnectAttempts = 0;
    },
    
    /**
     * Check if currently connected.
     * 
     * @returns {boolean} - true if connected
     */
    isConnected() {
        return this.connected;
    },
    
    /**
     * Get connection status for debugging.
     * 
     * @returns {Object} - Status object
     */
    getStatus() {
        return {
            connected: this.connected,
            connecting: this.connecting,
            roomCode: this.roomCode,
            reconnectAttempts: this.reconnectAttempts,
            hasSubscription: !!this.subscription
        };
    }
};
