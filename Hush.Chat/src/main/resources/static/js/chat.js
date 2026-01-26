// Chat Messaging Logic

const chat = {
    roomCode: null,
    userId: null,
    userName: null,
    joinTime: null,
    messagesContainer: null,
    messageInput: null,
    messageForm: null,
    displayedMessageIds: new Set(),
    expiryUpdateInterval: null,
    MESSAGE_TTL_MINUTES: 10, // Must match backend config
    
    /**
     * Initialize chat module
     */
    init() {
        this.roomCode = localStorage.getItem('roomCode');
        this.userId = localStorage.getItem('userId');
        this.userName = localStorage.getItem('userName');
        this.joinTime = new Date().toISOString();
        
        // Store join time for reference
        localStorage.setItem('joinTime', this.joinTime);
        
        this.messagesContainer = document.getElementById('messagesContainer');
        this.messageInput = document.getElementById('messageInput');
        this.messageForm = document.getElementById('messageForm');
        
        if (!this.roomCode || !this.userId || !this.userName) {
            console.error('Missing room or user info');
            window.location.href = 'index.html';
            return;
        }
        
        this.setupEventListeners();
        this.startExpiryCountdownUpdater();
        console.log('Chat initialized for room:', this.roomCode);
    },
    
    /**
     * Setup event listeners
     */
    setupEventListeners() {
        if (this.messageForm) {
            this.messageForm.addEventListener('submit', (e) => {
                e.preventDefault();
                this.sendMessage();
            });
        }
        
        // Handle Enter key (already handled by form submit)
        if (this.messageInput) {
            this.messageInput.addEventListener('keydown', (e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    this.sendMessage();
                }
            });
        }
    },
    
    /**
     * Start the expiry countdown updater
     * Updates all visible message countdowns every second
     */
    startExpiryCountdownUpdater() {
        // Update every second
        this.expiryUpdateInterval = setInterval(() => {
            this.updateAllExpiryCountdowns();
        }, 1000);
    },
    
    /**
     * Stop the expiry countdown updater
     */
    stopExpiryCountdownUpdater() {
        if (this.expiryUpdateInterval) {
            clearInterval(this.expiryUpdateInterval);
            this.expiryUpdateInterval = null;
        }
    },
    
    /**
     * Update all message expiry countdowns
     */
    updateAllExpiryCountdowns() {
        const messages = this.messagesContainer.querySelectorAll('.message[data-expiry-time]');
        const now = new Date();
        
        messages.forEach(messageDiv => {
            const expiryTimeStr = messageDiv.dataset.expiryTime;
            if (!expiryTimeStr) return;
            
            const expiryTime = new Date(expiryTimeStr);
            const remainingMs = expiryTime - now;
            
            if (remainingMs <= 0) {
                // Message has expired - remove it with animation
                this.removeExpiredMessage(messageDiv);
            } else {
                // Update countdown display
                this.updateExpiryDisplay(messageDiv, remainingMs);
            }
        });
    },
    
    /**
     * Update the expiry countdown display for a message
     */
    updateExpiryDisplay(messageDiv, remainingMs) {
        const expiryBadge = messageDiv.querySelector('.expiry-badge');
        if (!expiryBadge) return;
        
        const remainingSeconds = Math.floor(remainingMs / 1000);
        const minutes = Math.floor(remainingSeconds / 60);
        const seconds = remainingSeconds % 60;
        
        // Format as MM:SS
        const timeStr = `${minutes}:${seconds.toString().padStart(2, '0')}`;
        expiryBadge.textContent = `⏱ ${timeStr}`;
        
        // Visual urgency based on remaining time
        expiryBadge.classList.remove('expiry-warning', 'expiry-critical');
        
        if (remainingSeconds <= 60) {
            // Last minute - critical
            expiryBadge.classList.add('expiry-critical');
        } else if (remainingSeconds <= 180) {
            // Last 3 minutes - warning
            expiryBadge.classList.add('expiry-warning');
        }
    },
    
    /**
     * Remove an expired message with fade-out animation
     */
    removeExpiredMessage(messageDiv) {
        const messageId = messageDiv.dataset.messageId;
        
        // Add fade-out class
        messageDiv.classList.add('message-expiring');
        
        // Remove after animation completes
        setTimeout(() => {
            messageDiv.remove();
            this.displayedMessageIds.delete(messageId);
        }, 500);
    },
    
    /**
     * Send a message
     */
    async sendMessage() {
        const content = this.messageInput.value.trim();
        
        if (!content) {
            return;
        }
        
        // Disable input while sending
        this.messageInput.disabled = true;
        
        try {
            const response = await api.post('/messages/send', {
                roomCode: this.roomCode,
                userId: this.userId,
                content: content
            }, false);  // No auth required
            
            if (response.success) {
                // Clear input
                this.messageInput.value = '';
                
                // Render the sent message immediately
                this.renderMessage(response.data, true);
            } else {
                throw new Error(response.error || 'Failed to send message');
            }
        } catch (error) {
            console.error('Failed to send message:', error);
            
            if (error.message.includes('Room') && (error.message.includes('closed') || error.message.includes('expired') || error.message.includes('not found'))) {
                this.handleRoomClosed();
            } else {
                this.showError('Failed to send message. Please try again.');
            }
        } finally {
            this.messageInput.disabled = false;
            this.messageInput.focus();
        }
    },
    
    /**
     * Render a single message
     */
    renderMessage(message, isOwn = null) {
        // Prevent duplicate messages
        if (this.displayedMessageIds.has(message.messageId)) {
            return;
        }
        this.displayedMessageIds.add(message.messageId);
        
        // Determine if message is from current user
        if (isOwn === null) {
            isOwn = message.senderId === this.userId;
        }
        
        const messageDiv = document.createElement('div');
        messageDiv.className = `message ${isOwn ? 'message-own' : 'message-other'}`;
        messageDiv.dataset.messageId = message.messageId;
        
        // Store expiry time for countdown
        if (message.expiryTime) {
            messageDiv.dataset.expiryTime = message.expiryTime;
        }
        
        const timestamp = new Date(message.timestamp);
        const timeString = timestamp.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
        
        // Calculate initial remaining time
        const expiryTime = message.expiryTime ? new Date(message.expiryTime) : null;
        const remainingMs = expiryTime ? expiryTime - new Date() : this.MESSAGE_TTL_MINUTES * 60 * 1000;
        const remainingSeconds = Math.max(0, Math.floor(remainingMs / 1000));
        const minutes = Math.floor(remainingSeconds / 60);
        const seconds = remainingSeconds % 60;
        const expiryDisplay = `${minutes}:${seconds.toString().padStart(2, '0')}`;
        
        // Determine initial urgency class
        let urgencyClass = '';
        if (remainingSeconds <= 60) {
            urgencyClass = 'expiry-critical';
        } else if (remainingSeconds <= 180) {
            urgencyClass = 'expiry-warning';
        }
        
        messageDiv.innerHTML = `
            <div class="message-header">
                <span class="message-sender">${this.escapeHtml(message.senderName)}</span>
                <div class="message-meta">
                    <span class="expiry-badge ${urgencyClass}">⏱ ${expiryDisplay}</span>
                    <span class="message-time">${timeString}</span>
                </div>
            </div>
            <div class="message-content">${this.escapeHtml(message.content)}</div>
        `;
        
        this.messagesContainer.appendChild(messageDiv);
        this.scrollToBottom();
    },
    
    /**
     * Render multiple messages
     */
    renderMessages(messages) {
        messages.forEach(msg => {
            this.renderMessage(msg);
        });
    },
    
    /**
     * Clear welcome message if still showing
     */
    clearWelcomeMessage() {
        const welcomeMessage = this.messagesContainer.querySelector('.welcome-message');
        if (welcomeMessage && this.displayedMessageIds.size > 0) {
            // Keep welcome message but add a separator
        }
    },
    
    /**
     * Scroll to bottom of messages container
     */
    scrollToBottom() {
        if (this.messagesContainer) {
            this.messagesContainer.scrollTop = this.messagesContainer.scrollHeight;
        }
    },
    
    /**
     * Escape HTML to prevent XSS
     */
    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    },
    
    /**
     * Handle room closed/expired
     */
    handleRoomClosed() {
        // Stop expiry updater
        this.stopExpiryCountdownUpdater();
        
        // Stop polling
        if (typeof polling !== 'undefined') {
            polling.stopPolling();
        }
        
        // Show modal or alert
        const modal = document.getElementById('leaveModal');
        if (modal) {
            const modalContent = modal.querySelector('.modal-content');
            if (modalContent) {
                modalContent.innerHTML = `
                    <div class="room-closed-icon">🔒</div>
                    <h3>Room Closed</h3>
                    <p>This chat room has been closed due to inactivity or expiration.</p>
                    <p class="small-text">All messages have been permanently deleted.</p>
                    <div class="modal-actions">
                        <button id="returnHome" class="btn btn-primary">Return Home</button>
                    </div>
                `;
                modal.style.display = 'flex';
                
                document.getElementById('returnHome').addEventListener('click', () => {
                    this.cleanup();
                    window.location.href = 'index.html';
                });
            }
        } else {
            alert('This chat room has been closed or expired. All messages have been deleted.');
            this.cleanup();
            window.location.href = 'index.html';
        }
    },
    
    /**
     * Show error message
     */
    showError(message) {
        // Create a temporary error toast
        const toast = document.createElement('div');
        toast.className = 'error-toast';
        toast.textContent = message;
        toast.style.cssText = `
            position: fixed;
            bottom: 100px;
            left: 50%;
            transform: translateX(-50%);
            background: #ef4444;
            color: white;
            padding: 12px 24px;
            border-radius: 8px;
            z-index: 1000;
            animation: fadeIn 0.3s ease;
        `;
        document.body.appendChild(toast);
        
        setTimeout(() => {
            toast.remove();
        }, 3000);
    },
    
    /**
     * Cleanup on exit
     */
    cleanup() {
        this.stopExpiryCountdownUpdater();
        localStorage.removeItem('roomCode');
        localStorage.removeItem('roomName');
        localStorage.removeItem('userId');
        localStorage.removeItem('userName');
        localStorage.removeItem('isCreator');
        localStorage.removeItem('joinTime');
        localStorage.removeItem('lastMessageTime');
    }
};

// Initialize when DOM is ready
document.addEventListener('DOMContentLoaded', () => {
    chat.init();
});
