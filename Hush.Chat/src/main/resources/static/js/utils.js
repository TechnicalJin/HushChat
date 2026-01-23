// Utility Functions

/**
 * Show error message
 */
function showError(message) {
    const errorEl = document.getElementById('errorMessage');
    const successEl = document.getElementById('successMessage');

    if (successEl) successEl.style.display = 'none';

    if (errorEl) {
        errorEl.textContent = message;
        errorEl.style.display = 'block';

        // Auto-hide after 5 seconds
        setTimeout(() => {
            errorEl.style.display = 'none';
        }, 5000);
    }
}

/**
 * Show success message
 */
function showSuccess(message) {
    const successEl = document.getElementById('successMessage');
    const errorEl = document.getElementById('errorMessage');

    if (errorEl) errorEl.style.display = 'none';

    if (successEl) {
        successEl.textContent = message;
        successEl.style.display = 'block';

        // Auto-hide after 5 seconds
        setTimeout(() => {
            successEl.style.display = 'none';
        }, 5000);
    }
}

/**
 * Hide all messages
 */
function hideMessages() {
    const errorEl = document.getElementById('errorMessage');
    const successEl = document.getElementById('successMessage');

    if (errorEl) errorEl.style.display = 'none';
    if (successEl) successEl.style.display = 'none';
}

/**
 * Generate device ID if not exists
 */
function getOrCreateDeviceId() {
    let deviceId = localStorage.getItem(config.STORAGE_KEYS.DEVICE_ID);

    if (!deviceId) {
        // Generate a unique device ID
        deviceId = `device_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
        localStorage.setItem(config.STORAGE_KEYS.DEVICE_ID, deviceId);
    }

    return deviceId;
}

/**
 * Format timestamp to readable format
 */
function formatTimestamp(timestamp) {
    const date = new Date(timestamp);
    const now = new Date();
    const diffMs = now - date;
    const diffMins = Math.floor(diffMs / 60000);

    if (diffMins < 1) return 'Just now';
    if (diffMins < 60) return `${diffMins}m ago`;

    const diffHours = Math.floor(diffMins / 60);
    if (diffHours < 24) return `${diffHours}h ago`;

    return date.toLocaleDateString();
}

/**
 * Format time remaining
 */
function formatTimeRemaining(expiryTime) {
    const expiry = new Date(expiryTime);
    const now = new Date();
    const diffMs = expiry - now;

    if (diffMs <= 0) return 'Expired';

    const diffMins = Math.floor(diffMs / 60000);
    const diffSecs = Math.floor((diffMs % 60000) / 1000);

    return `${diffMins}:${diffSecs.toString().padStart(2, '0')}`;
}

/**
 * Validate device ID format
 */
function validateDeviceId(deviceId) {
    if (!deviceId || deviceId.trim().length < 10) {
        return 'Device ID must be at least 10 characters';
    }

    if (deviceId.length > 100) {
        return 'Device ID must be less than 100 characters';
    }

    return null; // Valid
}

/**
 * Validate OTP format
 */
function validateOtp(otp) {
    if (!otp || otp.trim().length !== 6) {
        return 'OTP must be exactly 6 digits';
    }

    if (!/^\d{6}$/.test(otp)) {
        return 'OTP must contain only numbers';
    }

    return null; // Valid
}

/**
 * Sanitize HTML to prevent XSS
 */
function sanitizeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

/**
 * Escape special characters in room code
 */
function sanitizeRoomCode(code) {
    return code.toUpperCase().replace(/[^A-Z0-9]/g, '');
}

/**
 * Copy text to clipboard
 */
async function copyToClipboard(text) {
    try {
        await navigator.clipboard.writeText(text);
        return true;
    } catch (err) {
        console.error('Failed to copy:', err);
        return false;
    }
}

/**
 * Check if session is expired
 */
function isSessionExpired(expiryTime) {
    if (!expiryTime) return true;

    const expiry = new Date(expiryTime);
    const now = new Date();

    return now >= expiry;
}

/**
 * Logout user
 */
async function logout() {
    try {
        // Call logout API
        await api.post('/auth/logout', {}, true);
    } catch (error) {
        console.error('Logout error:', error);
    } finally {
        // Clear local storage
        localStorage.removeItem(config.STORAGE_KEYS.SESSION_TOKEN);
        localStorage.removeItem(config.STORAGE_KEYS.USER_ID);
        localStorage.removeItem(config.STORAGE_KEYS.CURRENT_ROOM);

        // Redirect to home
        window.location.href = 'index.html';
    }
}

/**
 * Require authentication - redirect if not authenticated
 */
function requireAuth() {
    if (!api.isAuthenticated()) {
        window.location.href = 'index.html';
        return false;
    }
    return true;
}

/**
 * Debounce function
 */
function debounce(func, wait) {
    let timeout;
    return function executedFunction(...args) {
        const later = () => {
            clearTimeout(timeout);
            func(...args);
        };
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
    };
}

/**
 * Get file extension
 */
function getFileExtension(filename) {
    return filename.split('.').pop().toLowerCase();
}

/**
 * Format file size
 */
function formatFileSize(bytes) {
    if (bytes === 0) return '0 Bytes';

    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));

    return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
}

/**
 * Check if file type is allowed
 */
function isFileTypeAllowed(filename) {
    const ext = getFileExtension(filename);
    return config.ALLOWED_FILE_TYPES.includes(ext);
}