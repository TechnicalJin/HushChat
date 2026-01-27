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
    
    // Message actions state
    activeContextMenu: null,
    editingMessageId: null,
    originalMessageContent: null,
    
    // Scroll-to-bottom state
    scrollToBottomBtn: null,
    unreadBadge: null,
    unreadCount: 0,
    isUserAtBottom: true,
    scrollThreshold: 100, // px from bottom to consider "at bottom"
    
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
        
        // Initialize scroll-to-bottom elements
        this.scrollToBottomBtn = document.getElementById('scrollToBottomBtn');
        this.unreadBadge = document.getElementById('unreadBadge');
        
        if (!this.roomCode || !this.userId || !this.userName) {
            console.error('Missing room or user info');
            window.location.href = 'index.html';
            return;
        }
        
        this.setupEventListeners();
        this.setupScrollListener();
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
        
        // Handle Enter key for send, SHIFT+ENTER for new line
        if (this.messageInput) {
            this.messageInput.addEventListener('keydown', (e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    this.sendMessage();
                }
                // SHIFT+ENTER allows default behavior (new line)
            });
            
            // Auto-grow textarea as user types
            this.messageInput.addEventListener('input', () => {
                this.autoGrowTextarea();
            });
        }
        
        // Close context menu on click outside or escape
        document.addEventListener('click', (e) => {
            if (this.activeContextMenu && !e.target.closest('.message-context-menu') && !e.target.closest('.message-action-btn')) {
                this.closeContextMenu();
            }
        });
        
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape') {
                this.closeContextMenu();
                this.cancelEdit();
            }
        });
    },
    
    /**
     * Setup scroll listener for tracking scroll position
     */
    setupScrollListener() {
        if (!this.messagesContainer) return;
        
        this.messagesContainer.addEventListener('scroll', () => {
            this.checkScrollPosition();
        });
        
        // Setup scroll-to-bottom button click handler
        if (this.scrollToBottomBtn) {
            this.scrollToBottomBtn.addEventListener('click', () => {
                this.scrollToBottomSmooth();
            });
        }
        
        // Initial check
        this.checkScrollPosition();
    },
    
    /**
     * Check if user is at the bottom of the chat
     */
    checkScrollPosition() {
        if (!this.messagesContainer) return;
        
        const { scrollTop, scrollHeight, clientHeight } = this.messagesContainer;
        const distanceFromBottom = scrollHeight - scrollTop - clientHeight;
        
        const wasAtBottom = this.isUserAtBottom;
        this.isUserAtBottom = distanceFromBottom <= this.scrollThreshold;
        
        // User scrolled to bottom - reset unread count
        if (this.isUserAtBottom && !wasAtBottom) {
            this.resetUnreadCount();
        }
        
        // Update button visibility
        this.updateScrollButtonVisibility();
    },
    
    /**
     * Update the scroll-to-bottom button visibility
     */
    updateScrollButtonVisibility() {
        if (!this.scrollToBottomBtn) return;
        
        if (this.isUserAtBottom) {
            this.scrollToBottomBtn.classList.remove('visible');
            this.scrollToBottomBtn.style.display = 'none';
        } else {
            this.scrollToBottomBtn.style.display = 'flex';
            // Use requestAnimationFrame for smooth transition
            requestAnimationFrame(() => {
                this.scrollToBottomBtn.classList.add('visible');
            });
        }
    },
    
    /**
     * Increment unread count when new message arrives
     */
    incrementUnreadCount() {
        if (this.isUserAtBottom) return;
        
        this.unreadCount++;
        this.updateUnreadBadge();
    },
    
    /**
     * Reset unread count to zero
     */
    resetUnreadCount() {
        this.unreadCount = 0;
        this.updateUnreadBadge();
    },
    
    /**
     * Update the unread badge display
     */
    updateUnreadBadge() {
        if (!this.unreadBadge) return;
        
        if (this.unreadCount > 0) {
            this.unreadBadge.textContent = this.unreadCount > 99 ? '99+' : this.unreadCount;
            this.unreadBadge.style.display = 'flex';
        } else {
            this.unreadBadge.style.display = 'none';
        }
    },
    
    /**
     * Smooth scroll to bottom and reset unread count
     */
    scrollToBottomSmooth() {
        if (!this.messagesContainer) return;
        
        this.messagesContainer.scrollTo({
            top: this.messagesContainer.scrollHeight,
            behavior: 'smooth'
        });
        
        // Reset unread count immediately
        this.resetUnreadCount();
        
        // Hide button after scroll completes
        setTimeout(() => {
            this.isUserAtBottom = true;
            this.updateScrollButtonVisibility();
        }, 300);
    },
    
    /**
     * Auto-grow textarea based on content
     */
    autoGrowTextarea() {
        if (!this.messageInput) return;
        
        // Reset height to auto to get correct scrollHeight
        this.messageInput.style.height = 'auto';
        
        // Set new height based on content (max 150px defined in CSS)
        const newHeight = Math.min(this.messageInput.scrollHeight, 150);
        this.messageInput.style.height = newHeight + 'px';
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
        if (!this.messagesContainer) return;
        
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
        
        // Check if we're in edit mode
        if (this.editingMessageId) {
            this.saveEditedMessage(this.editingMessageId, content);
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
                // Clear input and reset height
                this.messageInput.value = '';
                this.messageInput.style.height = 'auto';
                
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
        // Determine if message is from current user
        if (isOwn === null) {
            isOwn = message.senderId === this.userId;
        }
        
        // Handle FILE type messages - use fileId as the unique key
        if (message.type === 'FILE' && message.fileId) {
            const fileMessageKey = 'file_' + message.fileId;
            if (this.displayedMessageIds.has(fileMessageKey)) {
                return;
            }
            this.displayedMessageIds.add(fileMessageKey);
            if (typeof fileHandler !== 'undefined') {
                fileHandler.renderFileMessage({
                    fileId: message.fileId,
                    originalFilename: message.fileName,
                    contentType: message.fileContentType,
                    fileSize: message.fileSize,
                    uploaderName: message.senderName,
                    uploadTime: message.timestamp,
                    expiryTime: message.expiryTime
                }, isOwn);
            }
            return;
        }
        
        // Prevent duplicate text messages
        if (this.displayedMessageIds.has(message.messageId)) {
            return;
        }
        this.displayedMessageIds.add(message.messageId);
        
        const messageDiv = document.createElement('div');
        messageDiv.className = `message ${isOwn ? 'message-own' : 'message-other'}`;
        messageDiv.dataset.messageId = message.messageId;
        
        // Store expiry time for countdown
        if (message.expiryTime) {
            messageDiv.dataset.expiryTime = message.expiryTime;
        }
        
        const timestamp = new Date(message.timestamp);
        const timeString = timestamp.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
        const editedIndicator = message.edited ? ' (edited)' : '';
        
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
        
        // Check if content looks like code (SQL, scripts, etc.)
        const isCodeContent = this.isCodeLikeContent(message.content);
        const lineCount = (message.content.match(/\n/g) || []).length;
        const isLongMessage = lineCount > 5 || message.content.length > 300;
        
        // Build content classes
        let contentClasses = 'message-content';
        if (isCodeContent) contentClasses += ' code-content';
        if (isLongMessage) contentClasses += ' collapsible collapsed';
        
        // Generate unique ID for this message's content
        const contentId = `content-${message.messageId}`;
        const btnId = `btn-${message.messageId}`;
        
        // Store original content for edit functionality
        messageDiv.dataset.originalContent = message.content;
        
        messageDiv.innerHTML = `
            <div class="message-header">
                <span class="message-sender">${this.escapeHtml(message.senderName)}</span>
                <div class="message-meta">
                    <span class="expiry-badge ${urgencyClass}">⏱ ${expiryDisplay}</span>
                    <span class="message-time">${timeString}${editedIndicator}</span>
                </div>
            </div>
            <div class="message-bubble-wrapper">
                <div id="${contentId}" class="${contentClasses}">${this.escapeHtml(message.content)}</div>
                ${isOwn ? `<button class="message-action-btn" data-message-id="${message.messageId}" title="Message options">▼</button>` : ''}
            </div>
            ${isLongMessage ? `<button id="${btnId}" class="read-more-btn" data-content-id="${contentId}">Read More ▼</button>` : ''}
        `;
        
        this.messagesContainer.appendChild(messageDiv);
        
        // Attach action button click handler for own messages
        if (isOwn) {
            const actionBtn = messageDiv.querySelector('.message-action-btn');
            if (actionBtn) {
                actionBtn.addEventListener('click', (e) => {
                    e.stopPropagation();
                    this.showContextMenu(e, message.messageId);
                });
            }
            
            // Right-click context menu for own messages
            messageDiv.addEventListener('contextmenu', (e) => {
                e.preventDefault();
                this.showContextMenu(e, message.messageId);
            });
        }
        
        // Attach click handler for Read More button
        if (isLongMessage) {
            const btn = document.getElementById(btnId);
            if (btn) {
                btn.addEventListener('click', (e) => this.toggleReadMore(e));
            }
        }
        
        // Handle scrolling and unread count
        if (isOwn) {
            // Own messages: always scroll to bottom
            this.forceScrollToBottom();
        } else {
            // Other's messages: increment unread if not at bottom
            if (!this.isUserAtBottom) {
                this.incrementUnreadCount();
            }
            this.scrollToBottom();
        }
    },
    
    /**
     * Toggle Read More / Show Less for long messages
     */
    toggleReadMore(event) {
        const btn = event.target;
        const contentId = btn.dataset.contentId;
        const content = document.getElementById(contentId);
        
        if (!content) return;
        
        if (content.classList.contains('collapsed')) {
            content.classList.remove('collapsed');
            content.classList.add('expanded');
            btn.textContent = 'Show Less ▲';
        } else {
            content.classList.remove('expanded');
            content.classList.add('collapsed');
            btn.textContent = 'Read More ▼';
        }
    },
    
    /**
     * Detect if content looks like code (SQL, scripts, logs, etc.)
     */
    isCodeLikeContent(content) {
        if (!content) return false;
        
        // Check for multiple line breaks (multi-line content)
        const lineCount = (content.match(/\n/g) || []).length;
        if (lineCount < 2) return false;
        
        // Check for common code patterns
        const codePatterns = [
            /={3,}/,                    // Line separators like ====
            /-{3,}/,                    // Dashed separators ---
            /SELECT|INSERT|UPDATE|DELETE|FROM|WHERE/i,  // SQL keywords
            /^\s*--\s/m,                // SQL comments
            /^\s*\/\//m,                // Single-line comments
            /^\s*#/m,                   // Hash comments (Python, Bash)
            /function\s*\(/,            // JavaScript functions
            /def\s+\w+\s*\(/,           // Python functions
            /\{\s*\n/,                  // Opening braces with newline
            /^\s{4,}\S/m,               // Indented code (4+ spaces)
            /^\t+\S/m,                  // Tab indented code
        ];
        
        return codePatterns.some(pattern => pattern.test(content));
    },
    
    /**
     * Render multiple messages
     */
    renderMessages(messages) {
        messages.forEach(msg => {
            // Determine the correct key for this message
            const messageKey = (msg.type === 'FILE' && msg.fileId) 
                ? 'file_' + msg.fileId 
                : msg.messageId;
            
            // Check if message already exists (for updates)
            if (this.displayedMessageIds.has(messageKey)) {
                // Update existing message if edited or deleted
                this.updateExistingMessage(msg);
            } else {
                // Render new message (skip deleted ones)
                if (!msg.deleted) {
                    this.renderMessage(msg);
                }
            }
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
     * Scroll to bottom of messages container (only if user is at bottom)
     */
    scrollToBottom() {
        if (this.messagesContainer) {
            if (this.isUserAtBottom) {
                this.messagesContainer.scrollTop = this.messagesContainer.scrollHeight;
            }
        }
    },
    
    /**
     * Force scroll to bottom (for own messages)
     */
    forceScrollToBottom() {
        if (this.messagesContainer) {
            this.messagesContainer.scrollTop = this.messagesContainer.scrollHeight;
            this.isUserAtBottom = true;
            this.resetUnreadCount();
            this.updateScrollButtonVisibility();
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
        
        // Hide scroll-to-bottom button
        if (this.scrollToBottomBtn) {
            this.scrollToBottomBtn.style.display = 'none';
            this.scrollToBottomBtn.classList.remove('visible');
        }
        this.unreadCount = 0;
        
        // Stop polling safely
        if (typeof polling !== 'undefined' && polling && typeof polling.stopPolling === 'function') {
            try {
                polling.stopPolling();
            } catch (e) {
                // Silently ignore polling stop errors
            }
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
     * Show context menu for message actions
     */
    showContextMenu(event, messageId) {
        // Close any existing menu
        this.closeContextMenu();
        
        const menu = document.createElement('div');
        menu.className = 'message-context-menu';
        menu.innerHTML = `
            <button class="context-menu-item" data-action="edit">
                <span class="context-menu-icon">✏️</span>
                <span>Edit Message</span>
            </button>
            <button class="context-menu-item context-menu-item-danger" data-action="unsend">
                <span class="context-menu-icon">🗑️</span>
                <span>Unsend Message</span>
            </button>
        `;
        
        document.body.appendChild(menu);
        this.activeContextMenu = menu;
        
        // Position menu near the click/button
        const menuWidth = 180;
        const menuHeight = menu.offsetHeight || 80;
        let x = event.clientX || event.pageX;
        let y = event.clientY || event.pageY;
        
        // Adjust if menu goes off screen
        if (x + menuWidth > window.innerWidth) {
            x = window.innerWidth - menuWidth - 10;
        }
        if (y + menuHeight > window.innerHeight) {
            y = window.innerHeight - menuHeight - 10;
        }
        
        menu.style.left = x + 'px';
        menu.style.top = y + 'px';
        
        // Add click handlers for menu items
        menu.querySelectorAll('.context-menu-item').forEach(item => {
            item.addEventListener('click', (e) => {
                const action = item.dataset.action;
                this.handleMessageAction(action, messageId);
                this.closeContextMenu();
            });
        });
    },
    
    /**
     * Close the context menu
     */
    closeContextMenu() {
        if (this.activeContextMenu) {
            this.activeContextMenu.remove();
            this.activeContextMenu = null;
        }
    },
    
    /**
     * Handle message action (edit or unsend)
     */
    handleMessageAction(action, messageId) {
        if (action === 'edit') {
            this.startEditMessage(messageId);
        } else if (action === 'unsend') {
            this.unsendMessage(messageId);
        }
    },
    
    /**
     * Start editing a message
     */
    startEditMessage(messageId) {
        const messageDiv = this.messagesContainer.querySelector(`[data-message-id="${messageId}"]`);
        if (!messageDiv) return;
        
        // Get original content from data attribute
        const originalContent = messageDiv.dataset.originalContent;
        if (!originalContent) return;
        
        // Store editing state
        this.editingMessageId = messageId;
        this.originalMessageContent = originalContent;
        
        // Load content into input box
        this.messageInput.value = originalContent;
        this.messageInput.focus();
        this.autoGrowTextarea();
        
        // Change send button to save
        const sendBtn = document.querySelector('.btn-send');
        if (sendBtn) {
            sendBtn.innerHTML = '<span>Save</span>';
            sendBtn.classList.add('btn-editing');
        }
        
        // Add cancel button if not exists
        if (!document.querySelector('.btn-cancel-edit')) {
            const cancelBtn = document.createElement('button');
            cancelBtn.type = 'button';
            cancelBtn.className = 'btn-cancel-edit';
            cancelBtn.innerHTML = '✕';
            cancelBtn.title = 'Cancel edit';
            cancelBtn.addEventListener('click', () => this.cancelEdit());
            this.messageForm.insertBefore(cancelBtn, sendBtn);
        }
        
        // Highlight the message being edited
        messageDiv.classList.add('message-editing');
    },
    
    /**
     * Cancel editing
     */
    cancelEdit() {
        if (!this.editingMessageId) return;
        
        // Remove highlight from message
        const messageDiv = this.messagesContainer.querySelector(`[data-message-id="${this.editingMessageId}"]`);
        if (messageDiv) {
            messageDiv.classList.remove('message-editing');
        }
        
        // Clear input
        this.messageInput.value = '';
        this.messageInput.style.height = 'auto';
        
        // Restore send button
        const sendBtn = document.querySelector('.btn-send');
        if (sendBtn) {
            sendBtn.innerHTML = '<span>Send</span>';
            sendBtn.classList.remove('btn-editing');
        }
        
        // Remove cancel button
        const cancelBtn = document.querySelector('.btn-cancel-edit');
        if (cancelBtn) {
            cancelBtn.remove();
        }
        
        // Clear editing state
        this.editingMessageId = null;
        this.originalMessageContent = null;
    },
    
    /**
     * Save edited message
     */
    async saveEditedMessage(messageId, newContent) {
        const messageDiv = this.messagesContainer.querySelector(`[data-message-id="${messageId}"]`);
        if (!messageDiv) return;
        
        try {
            // Call backend API to edit message
            const response = await api.put(`/messages/${this.roomCode}/${messageId}`, {
                userId: this.userId,
                content: newContent
            });
            
            if (response.success) {
                // Update the message content in the DOM
                const contentDiv = messageDiv.querySelector('.message-content');
                if (contentDiv) {
                    contentDiv.textContent = newContent;
                }
                
                // Update the stored original content
                messageDiv.dataset.originalContent = newContent;
                
                // Remove editing highlight
                messageDiv.classList.remove('message-editing');
                
                // Add edited indicator if not exists
                const timeSpan = messageDiv.querySelector('.message-time');
                if (timeSpan && !timeSpan.textContent.includes('(edited)')) {
                    timeSpan.textContent += ' (edited)';
                }
            } else {
                this.showError('Failed to edit message');
            }
        } catch (error) {
            console.error('Failed to edit message:', error);
            this.showError('Failed to edit message. Please try again.');
        }
        
        // Reset UI
        this.cancelEdit();
    },
    
    /**
     * Unsend (remove) a message
     */
    async unsendMessage(messageId) {
        const messageDiv = this.messagesContainer.querySelector(`[data-message-id="${messageId}"]`);
        if (!messageDiv) return;
        
        try {
            // Call backend API to unsend message
            const response = await api.delete(`/messages/${this.roomCode}/${messageId}?userId=${this.userId}`);
            
            if (response.success) {
                // Add fade out animation
                messageDiv.classList.add('message-unsending');
                
                // Remove after animation
                setTimeout(() => {
                    messageDiv.remove();
                    this.displayedMessageIds.delete(messageId);
                }, 300);
            } else {
                this.showError('Failed to unsend message');
            }
        } catch (error) {
            console.error('Failed to unsend message:', error);
            this.showError('Failed to unsend message. Please try again.');
        }
    },
    
    /**
     * Update an existing message in the DOM (for sync from polling)
     */
    updateExistingMessage(message) {
        const messageDiv = this.messagesContainer.querySelector(`[data-message-id="${message.messageId}"]`);
        if (!messageDiv) return false;
        
        // Handle deleted messages
        if (message.deleted) {
            messageDiv.classList.add('message-unsending');
            setTimeout(() => {
                messageDiv.remove();
                this.displayedMessageIds.delete(message.messageId);
            }, 300);
            return true;
        }
        
        // Handle edited messages
        if (message.edited) {
            const contentDiv = messageDiv.querySelector('.message-content');
            if (contentDiv && contentDiv.textContent !== message.content) {
                contentDiv.textContent = message.content;
                messageDiv.dataset.originalContent = message.content;
                
                // Add edited indicator if not exists
                const timeSpan = messageDiv.querySelector('.message-time');
                if (timeSpan && !timeSpan.textContent.includes('(edited)')) {
                    timeSpan.textContent += ' (edited)';
                }
            }
            return true;
        }
        
        return false;
    },
    
    /**
     * Cleanup on exit
     */
    cleanup() {
        this.stopExpiryCountdownUpdater();
        this.closeContextMenu();
        
        // Hide scroll-to-bottom button
        if (this.scrollToBottomBtn) {
            this.scrollToBottomBtn.style.display = 'none';
            this.scrollToBottomBtn.classList.remove('visible');
        }
        this.unreadCount = 0;
        
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
