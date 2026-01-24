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
            
            if (error.message.includes('Room') && error.message.includes('closed')) {
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
        
        const timestamp = new Date(message.timestamp);
        const timeString = timestamp.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
        
        messageDiv.innerHTML = `
            <div class="message-header">
                <span class="message-sender">${this.escapeHtml(message.senderName)}</span>
                <span class="message-time">${timeString}</span>
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
        // Show modal or alert
        const modal = document.getElementById('leaveModal');
        if (modal) {
            const modalContent = modal.querySelector('.modal-content');
            if (modalContent) {
                modalContent.innerHTML = `
                    <h3>Room Closed</h3>
                    <p>This chat room has been closed or expired.</p>
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
            alert('This chat room has been closed or expired.');
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
