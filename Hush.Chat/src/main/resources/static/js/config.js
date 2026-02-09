// API Configuration
// NOTE: Long polling support intentionally removed. WebSocket is the ONLY real-time transport.
const config = {
    // API Base URL - Update this for production
    API_BASE_URL: 'http://localhost:8080/api',

    // Timeouts
    REQUEST_TIMEOUT: 30000, // 30 seconds

    // OTP Settings
    OTP_LENGTH: 6,
    OTP_EXPIRY_MINUTES: 3,
    MAX_OTP_RETRIES: 3,

    // Session Settings
    SESSION_EXPIRY_HOURS: 24,

    // Message Settings
    MESSAGE_TTL_MINUTES: 10,

    // Room Settings
    ROOM_CODE_LENGTH: 5,
    MAX_USERS_PER_ROOM: 10,

    // File Upload Settings
    MAX_FILE_SIZE_MB: 200,
    ALLOWED_FILE_TYPES: ['jpg', 'jpeg', 'png', 'pdf', 'txt', 'docx', 'xlsx'],

    // Storage Keys
    STORAGE_KEYS: {
        DEVICE_ID: 'deviceId',
        SESSION_TOKEN: 'sessionToken',
        USER_ID: 'userId',
        CURRENT_ROOM: 'currentRoom',
        LAST_MESSAGE_TIME: 'lastMessageTime'
    },

    // WebSocket Configuration
    WEBSOCKET: {
        ENABLED: true,                      // WebSocket enabled (Primary)
        ENDPOINT: '/ws',                    // WebSocket endpoint
        RECONNECT_ATTEMPTS: 3,              // Max reconnection attempts
        RECONNECT_DELAYS: [1000, 3000, 5000] // Backoff delays in ms
    },

    // Polling Configuration (Fallback)
    POLLING: {
        ENABLED: true,                      // Polling enabled (Fallback)
        INTERVAL: 3000                      // 3 seconds
    }
};

// Environment detection
if (window.location.hostname !== 'localhost' && window.location.hostname !== '127.0.0.1') {
    // Production - Update with your production API URL
    config.API_BASE_URL = window.location.origin + '/api';
}

// Freeze config to prevent modifications
Object.freeze(config);