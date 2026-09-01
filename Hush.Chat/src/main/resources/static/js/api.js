// API Wrapper with authentication and error handling
const api = {
    /**
     * Get session token from storage
     */
    getSessionToken() {
        return localStorage.getItem(config.STORAGE_KEYS.SESSION_TOKEN);
    },

    /**
     * Get authorization headers
     */
    getAuthHeaders() {
        const token = this.getSessionToken();
        const headers = {
            'Content-Type': 'application/json'
        };

        if (token) {
            headers['Authorization'] = `Bearer ${token}`;
        }

        return headers;
    },

    /**
     * Make GET request
     */
    async get(endpoint, requiresAuth = true, timeoutMs = null) {
        try {
            const headers = requiresAuth ? this.getAuthHeaders() : {
                'Content-Type': 'application/json'
            };

            const effectiveTimeout = timeoutMs || config.REQUEST_TIMEOUT;

            const response = await fetch(`${config.API_BASE_URL}${endpoint}`, {
                method: 'GET',
                headers: headers,
                signal: AbortSignal.timeout(effectiveTimeout)
            });

            return await this.handleResponse(response);
        } catch (error) {
            throw this.handleError(error);
        }
    },

    /**
     * Make POST request
     */
    async post(endpoint, data, requiresAuth = true) {
        try {
            const headers = requiresAuth ? this.getAuthHeaders() : {
                'Content-Type': 'application/json'
            };

            const response = await fetch(`${config.API_BASE_URL}${endpoint}`, {
                method: 'POST',
                headers: headers,
                body: JSON.stringify(data),
                signal: AbortSignal.timeout(config.REQUEST_TIMEOUT)
            });

            return await this.handleResponse(response);
        } catch (error) {
            throw this.handleError(error);
        }
    },

    /**
     * Make PUT request
     */
    async put(endpoint, data, requiresAuth = true) {
        try {
            const headers = requiresAuth ? this.getAuthHeaders() : {
                'Content-Type': 'application/json'
            };

            const response = await fetch(`${config.API_BASE_URL}${endpoint}`, {
                method: 'PUT',
                headers: headers,
                body: JSON.stringify(data),
                signal: AbortSignal.timeout(config.REQUEST_TIMEOUT)
            });

            return await this.handleResponse(response);
        } catch (error) {
            throw this.handleError(error);
        }
    },

    /**
     * Make DELETE request
     */
    async delete(endpoint, requiresAuth = true) {
        try {
            const headers = requiresAuth ? this.getAuthHeaders() : {
                'Content-Type': 'application/json'
            };

            const response = await fetch(`${config.API_BASE_URL}${endpoint}`, {
                method: 'DELETE',
                headers: headers,
                signal: AbortSignal.timeout(config.REQUEST_TIMEOUT)
            });

            return await this.handleResponse(response);
        } catch (error) {
            throw this.handleError(error);
        }
    },

    /**
     * Upload file with multipart/form-data
     */
    async uploadFile(endpoint, formData) {
        try {
            const token = this.getSessionToken();
            const headers = {};

            if (token) {
                headers['Authorization'] = `Bearer ${token}`;
            }

            const response = await fetch(`${config.API_BASE_URL}${endpoint}`, {
                method: 'POST',
                headers: headers,
                body: formData,
                signal: AbortSignal.timeout(config.REQUEST_TIMEOUT * 3) // Longer timeout for uploads
            });

            return await this.handleResponse(response);
        } catch (error) {
            throw this.handleError(error);
        }
    },

    /**
     * Download a file as a blob with authorization header.
     * Returns the raw Response object (caller reads as blob).
     */
    async download(endpoint) {
        try {
            const token = this.getSessionToken();
            const headers = {};
            if (token) {
                headers['Authorization'] = `Bearer ${token}`;
            }

            const response = await fetch(`${config.API_BASE_URL}${endpoint}`, {
                method: 'GET',
                headers: headers,
                signal: AbortSignal.timeout(config.REQUEST_TIMEOUT * 3)
            });

            if (!response.ok) {
                throw new Error(`Download failed with status ${response.status}`);
            }

            return response;
        } catch (error) {
            throw this.handleError(error);
        }
    },

    /**
     * Handle API response
     */
    async handleResponse(response) {
        const contentType = response.headers.get('content-type');

        // Handle JSON responses
        if (contentType && contentType.includes('application/json')) {
            const data = await response.json();

            if (!response.ok) {
                throw new Error(data.error || data.message || 'Request failed');
            }

            return data;
        }

        // Handle non-JSON responses (e.g., file downloads)
        if (!response.ok) {
            throw new Error(`Request failed with status ${response.status}`);
        }

        return response;
    },

    /**
     * Handle API errors
     */
    handleError(error) {
        // Don't log timeout errors as errors (can be expected behavior)
        const isTimeout = error.name === 'TimeoutError' || 
                          error.name === 'AbortError' ||
                          (error.message && error.message.includes('timed out'));
        
        if (!isTimeout) {
            console.error('API Error:', error);
        }

        if (error.name === 'AbortError' || error.name === 'TimeoutError') {
            return new Error('Request timeout. Please check your connection.');
        }

        if (error.message && error.message.includes('Failed to fetch')) {
            return new Error('Cannot connect to server. Please check if the server is running.');
        }

        if (error.message && (error.message.includes('Unauthorized') || error.message.includes('Session'))) {
            // Clear session and redirect to login
            localStorage.removeItem(config.STORAGE_KEYS.SESSION_TOKEN);
            localStorage.removeItem(config.STORAGE_KEYS.USER_ID);

            if (window.location.pathname !== '/index.html' && window.location.pathname !== '/') {
                setTimeout(() => {
                    window.location.href = 'index.html';
                }, 2000);
            }
        }

        return error;
    },

    /**
     * Check if user is authenticated
     */
    isAuthenticated() {
        return !!this.getSessionToken();
    },

    /**
     * Validate current session
     */
    async validateSession() {
        try {
            const response = await this.get('/auth/validate', true);
            return response.success;
        } catch (error) {
            return false;
        }
    }
};