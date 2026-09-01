// Authentication Logic for index.html

// Check if already authenticated
if (api.isAuthenticated()) {
    // Validate session
    api.validateSession().then(isValid => {
        if (isValid) {
            window.location.href = 'chat.html';
        }
    });
}

// Handle OTP Request Form
document.getElementById('otpRequestForm')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    hideMessages();

    const deviceIdInput = document.getElementById('deviceId');
    const deviceId = deviceIdInput.value.trim();
    const requestBtn = document.getElementById('requestOtpBtn');

    // Validate device ID
    const validationError = validateDeviceId(deviceId);
    if (validationError) {
        showError(validationError);
        return;
    }

    // Disable button and show loading
    requestBtn.disabled = true;
    requestBtn.textContent = 'Requesting OTP...';

    try {
        // Request OTP (anonymous endpoint - no session token exists yet)
        const response = await api.post('/auth/request-otp', { deviceId }, false);

        if (response.success) {
            // Store device ID for OTP verification page
            localStorage.setItem(config.STORAGE_KEYS.DEVICE_ID, deviceId);

            showSuccess('OTP sent successfully! Redirecting...');

            // Redirect to OTP verification page
            setTimeout(() => {
                window.location.href = 'otp.html';
            }, 1500);
        } else {
            throw new Error(response.error || 'Failed to request OTP');
        }
    } catch (error) {
        showError(error.message || 'Failed to request OTP. Please try again.');

        // Re-enable button
        requestBtn.disabled = false;
        requestBtn.textContent = 'Request OTP';
    }
});

// Auto-fill device ID if exists
const savedDeviceId = localStorage.getItem(config.STORAGE_KEYS.DEVICE_ID);
if (savedDeviceId && document.getElementById('deviceId')) {
    document.getElementById('deviceId').value = savedDeviceId;
}

// Device ID input validation
document.getElementById('deviceId')?.addEventListener('input', (e) => {
    const deviceId = e.target.value.trim();
    const validationError = validateDeviceId(deviceId);

    if (validationError && deviceId.length > 0) {
        e.target.style.borderColor = 'var(--error-color)';
    } else {
        e.target.style.borderColor = 'var(--border-color)';
    }
});