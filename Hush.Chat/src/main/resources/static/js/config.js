// API Configuration
const config = {
    // API Base URL - Update this for production
    API_BASE_URL: 'http://localhost:8080/api',

    // Timeouts
    REQUEST_TIMEOUT: 30000, // 30 seconds
    LONG_POLL_TIMEOUT: 35000, // 35 seconds for long polling (server waits 20s + network buffer)

    // OTP Settings
    OTP_LENGTH: 6,
    OTP_EXPIRY_MINUTES: 3,
    MAX_OTP_RETRIES: 3,

    // Session Settings
    SESSION_EXPIRY_HOURS: 24,

    // Message Settings
    MESSAGE_TTL_MINUTES: 10,
    POLL_INTERVAL_MS: 2000, // 2 seconds

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
    }
};

// Environment detection
if (window.location.hostname !== 'localhost' && window.location.hostname !== '127.0.0.1') {
    // Production - Update with your production API URL
    config.API_BASE_URL = window.location.origin + '/api';
}

// Freeze config to prevent modifications
Object.freeze(config);