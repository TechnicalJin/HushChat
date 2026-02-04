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
    activeContextMessageId: null, // FIX 12: Track which message has context menu open
    
    // Reply system state
    replyToMessage: null, // Stores the message being replied to
    replyPreviewWrapper: null, // FIX 8: Wrapper element for smooth animation
    
    // FIX 1: Improved swipe gesture state (mobile)
    touchStartX: 0,
    touchStartY: 0,
    touchCurrentX: 0,
    touchCurrentY: 0,
    swipeThreshold: 80, // px needed to trigger reply
    activeSwipeElement: null,
    isScrolling: false, // FIX: Detect vertical scrolling vs horizontal swipe
    touchStartTime: 0, // FIX: Track touch duration for gesture differentiation
    
    // FIX 7: Message grouping - track last sender for clustering
    lastRenderedSenderId: null,
    
    // Scroll-to-bottom state
    scrollToBottomBtn: null,
    unreadBadge: null,
    unreadCount: 0,
    isUserAtBottom: true,
    scrollThreshold: 100, // px from bottom to consider "at bottom"
    
    // FIX 10: Toast notification queue
    activeToast: null,
    
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
        
        // FIX 8: Create reply preview wrapper for smooth height animation
        this.setupReplyPreviewWrapper();
        
        if (!this.roomCode || !this.userId || !this.userName) {
            console.error('Missing room or user info');
            window.location.href = 'index.html';
            return;
        }
        
        this.setupEventListeners();
        this.setupScrollListener();
        this.setupReplyPreviewListeners();
        this.setupMobileViewportFix(); // FIX: Handle mobile viewport issues
        this.startExpiryCountdownUpdater();
        console.log('Chat initialized for room:', this.roomCode);
    },
    
    /**
     * FIX 8: Setup reply preview wrapper for smooth height animation
     * The wrapper is now in HTML, we just need to get the reference
     */
    setupReplyPreviewWrapper() {
        const wrapper = document.getElementById('replyPreviewWrapper');
        if (wrapper) {
            this.replyPreviewWrapper = wrapper;
        }
    },
    
    /**
     * FIX: Handle mobile viewport height issues
     * Mobile browsers have dynamic toolbars that change viewport height
     * This ensures input bar is always visible
     */
    setupMobileViewportFix() {
        // Only apply on mobile
        if (!/Android|iPhone|iPad|iPod/i.test(navigator.userAgent)) return;
        
        // Use visualViewport API if available (better than resize event)
        if (window.visualViewport) {
            const handleViewportChange = () => {
                // Set CSS custom property for actual viewport height
                const vh = window.visualViewport.height * 0.01;
                document.documentElement.style.setProperty('--vh', `${vh}px`);
                
                // Ensure input bar is visible when keyboard opens
                const inputContainer = document.querySelector('.message-input-container');
                if (inputContainer) {
                    // Scroll input into view if keyboard is open
                    if (window.visualViewport.height < window.innerHeight * 0.7) {
                        // Keyboard is likely open
                        inputContainer.scrollIntoView({ behavior: 'smooth', block: 'end' });
                    }
                }
            };
            
            window.visualViewport.addEventListener('resize', handleViewportChange);
            window.visualViewport.addEventListener('scroll', handleViewportChange);
            handleViewportChange(); // Initial call
        } else {
            // Fallback for browsers without visualViewport API
            const setVh = () => {
                const vh = window.innerHeight * 0.01;
                document.documentElement.style.setProperty('--vh', `${vh}px`);
            };
            window.addEventListener('resize', setVh);
            setVh();
        }
    },
    
    /**
     * Setup reply preview bar listeners
     */
    setupReplyPreviewListeners() {
        const closeBtn = document.getElementById('replyPreviewClose');
        if (closeBtn) {
            closeBtn.addEventListener('click', () => this.cancelReply());
        }
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

        // File picker button
        const filePickerBtn = document.getElementById('filePickerBtn');
        const fileInput = document.getElementById('fileInput');
        if (filePickerBtn && fileInput) {
            filePickerBtn.addEventListener('click', () => {
                fileInput.click();
            });

            fileInput.addEventListener('change', (e) => {
                const file = e.target.files[0];
                if (file) {
                    this.handleFileSelected(file);
                }
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
        
        // FIX: Also close context menu on scroll (mobile UX improvement)
        if (this.messagesContainer) {
            this.messagesContainer.addEventListener('scroll', () => {
                if (this.activeContextMenu) {
                    this.closeContextMenu();
                }
            }, { passive: true });
        }
        
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape') {
                this.closeContextMenu();
                this.cancelEdit();
                this.cancelReply();
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
     * FIX 9: Auto-grow textarea based on content with smooth animation
     * Max height is 4-5 lines, then internal scroll
     */
    autoGrowTextarea() {
        if (!this.messageInput) return;
        
        // Store current scroll position to prevent jump
        const scrollPos = this.messagesContainer?.scrollTop;
        
        // Reset height to auto to get correct scrollHeight
        this.messageInput.style.height = 'auto';
        
        // Get max height from CSS (120px on mobile, 150px on desktop)
        const isMobile = window.innerWidth <= 768;
        const maxHeight = isMobile ? 120 : 150;
        
        // Set new height based on content
        const newHeight = Math.min(this.messageInput.scrollHeight, maxHeight);
        this.messageInput.style.height = newHeight + 'px';
        
        // Restore scroll position to prevent jump
        if (this.messagesContainer && scrollPos !== undefined) {
            this.messagesContainer.scrollTop = scrollPos;
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
     * Send a message (with optional reply)
     */
    async sendMessage() {
        const content = this.messageInput.value.trim();

        // If no text but a file is selected, let file handler send
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
            // Build request payload with optional reply metadata
            const payload = {
                roomCode: this.roomCode,
                userId: this.userId,
                content: content
            };
            
            // Attach reply metadata if replying to a message
            if (this.replyToMessage) {
                payload.replyTo = {
                    messageId: this.replyToMessage.messageId,
                    senderId: this.replyToMessage.senderId,
                    senderName: this.replyToMessage.senderName,
                    messageType: this.replyToMessage.type || 'TEXT',
                    previewText: this.replyToMessage.content
                };
            }
            
            const response = await api.post('/messages/send', payload, false);
            
            if (response.success) {
                // Clear input and reset height
                this.messageInput.value = '';
                this.messageInput.style.height = 'auto';
                
                // Clear reply state
                this.cancelReply();
                
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
     * Handle file selection and upload
     */
    async handleFileSelected(file) {
        // Validate type
        if (!isFileTypeAllowed(file.name)) {
            this.showError('File type not allowed.');
            return;
        }

        // Validate size
        const sizeMb = file.size / (1024 * 1024);
        if (sizeMb > config.MAX_FILE_SIZE_MB) {
            this.showError(`File too large. Max ${config.MAX_FILE_SIZE_MB}MB allowed.`);
            return;
        }

        const statusEl = document.getElementById('fileUploadStatus');
        const nameEl = document.getElementById('fileUploadName');
        const progressEl = document.getElementById('fileUploadProgress');

        if (statusEl && nameEl && progressEl) {
            statusEl.style.display = 'flex';
            nameEl.textContent = `${file.name} (${formatFileSize(file.size)})`;
            progressEl.textContent = 'Uploading...';
        }

        try {
            const formData = new FormData();
            formData.append('roomCode', this.roomCode);
            formData.append('userId', this.userId);
            formData.append('senderName', this.userName);
            formData.append('file', file);

            const response = await api.uploadFile('/files/upload', formData);

            if (response.success) {
                // Render as a file message
                const data = response.data;
                const message = {
                    messageId: data.fileId,
                    roomCode: data.roomCode,
                    senderId: data.senderId,
                    senderName: data.senderName,
                    content: data.originalFilename,
                    type: 'FILE',
                    timestamp: data.uploadTime,
                    expiryTime: data.expiryTime,
                    edited: false,
                    deleted: false,
                    lastModified: data.uploadTime,
                    downloadUrl: data.downloadUrl,
                    fileSize: data.fileSize,
                    contentType: data.contentType
                };
                this.renderMessage(message, true);
                if (progressEl) {
                    progressEl.textContent = 'Uploaded';
                }
            } else {
                throw new Error(response.error || 'Failed to upload file');
            }
        } catch (error) {
            console.error('Failed to upload file:', error);
            this.showError(error.message || 'Failed to upload file.');
        } finally {
            // Reset input
            const fileInput = document.getElementById('fileInput');
            if (fileInput) {
                fileInput.value = '';
            }
            // Hide status after short delay
            if (statusEl) {
                setTimeout(() => {
                    statusEl.style.display = 'none';
                }, 1500);
            }
        }
    },
    
    /**
     * Render a single message
     * FIX 7: Implements message grouping for same-sender clusters
     * FIX 13: Implements welcome message fade-out on first message
     */
    renderMessage(message, isOwn = null) {
        // Prevent duplicate messages
        if (this.displayedMessageIds.has(message.messageId)) {
            return;
        }
        this.displayedMessageIds.add(message.messageId);
        
        // FIX 13: Fade out welcome message on first real message
        if (this.displayedMessageIds.size === 1) {
            const welcomeMessage = this.messagesContainer.querySelector('.welcome-message');
            if (welcomeMessage) {
                welcomeMessage.classList.add('fading-out');
                // Remove from DOM after animation completes
                setTimeout(() => {
                    welcomeMessage.remove();
                }, 500);
            }
        }
        
        // Determine if message is from current user
        if (isOwn === null) {
            isOwn = message.senderId === this.userId;
        }
        
        // FIX 7: Message grouping - check if same sender as previous message
        const isSameSender = this.lastRenderedSenderId === message.senderId;
        const showSenderName = !isSameSender;
        
        const messageDiv = document.createElement('div');
        const isFile = message.type === 'FILE';
        
        // Build class list with grouping classes
        let classNames = `message ${isOwn ? 'message-own' : 'message-other'}`;
        if (isFile) classNames += ' message-file';
        if (isSameSender) {
            classNames += ' message-grouped';
        } else {
            classNames += ' message-new-sender';
        }
        
        messageDiv.className = classNames;
        messageDiv.dataset.messageId = message.messageId;
        messageDiv.dataset.senderId = message.senderId;
        // FIX: Store sender name in data attribute for reliable retrieval (even when header hidden)
        messageDiv.dataset.senderName = message.senderName || '';
        
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
        const isCodeContent = !isFile && this.isCodeLikeContent(message.content);
        const lineCount = isFile ? 1 : (message.content.match(/\n/g) || []).length;
        const isLongMessage = !isFile && (lineCount > 5 || message.content.length > 300);
        
        // Build content classes
        let contentClasses = 'message-content';
        if (isCodeContent) contentClasses += ' code-content';
        if (isLongMessage) contentClasses += ' collapsible collapsed';
        
        // Generate unique ID for this message's content
        const contentId = `content-${message.messageId}`;
        const btnId = `btn-${message.messageId}`;
        
        // Store original content for edit functionality
        messageDiv.dataset.originalContent = message.content;
        
        // FIX 7: Update last rendered sender for grouping (already calculated above)
        // Update AFTER we've used the comparison
        
        let bodyHtml;
        if (isFile) {
            const fileLabel = this.escapeHtml(message.content);
            const fileSizeLabel = message.fileSize ? ` (${formatFileSize(message.fileSize)})` : '';
            const downloadUrl = message.downloadUrl || `${config.API_BASE_URL}/files/${message.fileId || message.messageId}/download`;
            const isImage = /\.(png|jpe?g)$/i.test(message.content || '');
            const mediaBlock = isImage
                ? `<div class="file-thumb"><img src="${downloadUrl}" alt="${fileLabel}"></div>`
                : `<div class="file-icon">📎</div>`;
            bodyHtml = `
                <div id="${contentId}" class="${contentClasses} file-message-content">
                    ${mediaBlock}
                    <div class="file-info">
                        <a href="${downloadUrl}" class="file-name" data-file-type="${isImage ? 'image' : 'file'}">
                            ${fileLabel}
                        </a>
                        <div class="file-meta-text">
                            <span>${fileSizeLabel}</span>
                        </div>
                    </div>
                </div>
            `;
        } else {
            bodyHtml = `<div id="${contentId}" class="${contentClasses}">${this.escapeHtml(message.content)}</div>`;
        }
        
        // Build reply container HTML if this message is a reply
        const replyContainerHtml = this.buildReplyContainerHtml(message);

        const senderHeaderHtml = showSenderName
            ? `<div class="message-header">
                    <span class="message-sender">${this.escapeHtml(message.senderName)}</span>
               </div>`
            : '';

        const footerHtml = `
            <div class="message-footer ${isOwn ? 'message-footer-own' : 'message-footer-other'}">
                <span class="expiry-badge ${urgencyClass}">⏱ ${expiryDisplay}</span>
                <span class="message-time">${timeString}${editedIndicator}</span>
            </div>
        `;

        // Action button shown for ALL messages (not just own) for reply/copy access
        messageDiv.innerHTML = `
            ${senderHeaderHtml}
            <div class="message-bubble-wrapper">
                ${replyContainerHtml}
                ${bodyHtml}
                <button class="message-action-btn" data-message-id="${message.messageId}" title="Message options">▼</button>
            </div>
            ${isLongMessage ? `<button id="${btnId}" class="read-more-btn" data-content-id="${contentId}">Read More ▼</button>` : ''}
            ${footerHtml}
        `;
        
        this.messagesContainer.appendChild(messageDiv);
        
        // FIX 7: Update last rendered sender AFTER appending to maintain grouping state
        this.lastRenderedSenderId = message.senderId;
        
        // Attach reply container click handler for scroll-to-original
        const replyContainer = messageDiv.querySelector('.reply-container');
        if (replyContainer && message.replyTo && !message.replyTo.deleted) {
            replyContainer.addEventListener('click', () => {
                this.scrollToMessage(message.replyTo.messageId);
            });
        }
        
        // Attach file click handler for image preview
        if (isFile) {
            const fileLink = messageDiv.querySelector('.file-name');
            if (fileLink) {
                const isImage = fileLink.dataset.fileType === 'image';
                const downloadUrl = fileLink.getAttribute('href');
                if (isImage) {
                    fileLink.addEventListener('click', (e) => {
                        e.preventDefault();
                        this.openImagePreview(downloadUrl);
                    });
                } else {
                    fileLink.setAttribute('target', '_blank');
                    fileLink.setAttribute('rel', 'noopener noreferrer');
                }
            }
        }

        // Attach action button click handler for ALL messages
        const actionBtn = messageDiv.querySelector('.message-action-btn');
        if (actionBtn) {
            actionBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                this.showContextMenu(e, message.messageId, isOwn);
            });
        }
        
        // Right-click context menu for ALL messages
        messageDiv.addEventListener('contextmenu', (e) => {
            e.preventDefault();
            this.showContextMenu(e, message.messageId, isOwn);
        });
        
        // Setup swipe-to-reply for mobile (touch devices)
        this.setupSwipeToReply(messageDiv, message);
        
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
     * Build HTML for reply container (shown inside message bubble)
     * FIX 5: Shows message type indicators
     * FIX 6: Improved deleted message styling
     * FIX: Shows "You" for own message replies
     */
    buildReplyContainerHtml(message) {
        if (!message.replyTo) return '';
        
        const replyTo = message.replyTo;
        
        // FIX 6: If original message was deleted - show with trash icon and italic
        if (replyTo.deleted) {
            return `
                <div class="reply-container reply-container-deleted">
                    <span class="reply-deleted-text">Original message deleted</span>
                </div>
            `;
        }
        
        // FIX 5: Get type icon based on message type for clarity
        const typeIcon = this.getMessageTypeIcon(replyTo.messageType);
        
        // FIX: Show "You" for replies to own messages
        const isOwnReply = replyTo.senderId === this.userId;
        const displayName = isOwnReply ? 'You' : (replyTo.senderName || 'Unknown');
        
        // Truncate preview text
        let previewText = replyTo.previewText || '';
        if (previewText.length > 50) {
            previewText = previewText.substring(0, 50) + '...';
        }
        
        return `
            <div class="reply-container" data-reply-message-id="${replyTo.messageId}">
                <span class="reply-sender">${this.escapeHtml(displayName)}</span>
                <span class="reply-text">
                    <span class="reply-type-icon">${typeIcon}</span>
                    ${this.escapeHtml(previewText)}
                </span>
            </div>
        `;
    },
    
    /**
     * FIX 5: Get icon for message type - provides visual clarity for reply types
     */
    getMessageTypeIcon(type) {
        switch (type) {
            case 'FILE':
                return '📎';
            case 'IMAGE':
                return '🖼️';
            case 'VIDEO':
                return '🎬';
            case 'PDF':
                return '📄';
            case 'TEXT':
            default:
                return '💬';
        }
    },
    
    /**
     * FIX 6: Scroll to a specific message and highlight it
     * Disabled for deleted messages
     */
    scrollToMessage(messageId) {
        const targetMessage = this.messagesContainer.querySelector(`[data-message-id="${messageId}"]`);
        if (targetMessage) {
            // Scroll to message
            targetMessage.scrollIntoView({ behavior: 'smooth', block: 'center' });
            
            // Add highlight animation
            targetMessage.classList.add('message-highlighted');
            
            // Remove highlight after animation
            setTimeout(() => {
                targetMessage.classList.remove('message-highlighted');
            }, 1500);
        } else {
            // Message not found (might have expired or been deleted)
            this.showToast('Original message is no longer available', 'info');
        }
    },
    
    /**
     * FIX 1: Setup swipe-to-reply and long-press for context menu on mobile
     * CRITICAL: Prevents gesture conflicts between scroll, swipe, and long-press
     * - Swipe right → Reply
     * - Long press → Open context menu
     * - Single tap → Do nothing
     * - Vertical scroll → Normal scrolling (never triggers menu or reply)
     */
    setupSwipeToReply(messageDiv, message) {
        let startX = 0;
        let startY = 0;
        let currentX = 0;
        let currentY = 0;
        let isSwiping = false;
        let isScrolling = false;
        let longPressTimer = null;
        let touchStartTime = 0;
        let hasMoved = false;
        
        const LONG_PRESS_DURATION = 500; // ms
        const SWIPE_THRESHOLD = 80; // px to trigger reply
        const SCROLL_THRESHOLD = 15; // px of vertical movement to consider scrolling
        const MOVEMENT_THRESHOLD = 10; // px of any movement to cancel long-press
        
        // FIX: Use userId for ownership check
        const isOwn = message.senderId === this.userId;
        
        const cancelLongPress = () => {
            if (longPressTimer) {
                clearTimeout(longPressTimer);
                longPressTimer = null;
            }
        };
        
        const resetSwipeVisual = () => {
            messageDiv.style.transform = '';
            messageDiv.style.transition = 'transform 0.2s ease';
        };
        
        messageDiv.addEventListener('touchstart', (e) => {
            startX = e.touches[0].clientX;
            startY = e.touches[0].clientY;
            currentX = startX;
            currentY = startY;
            touchStartTime = Date.now();
            isSwiping = true;
            isScrolling = false;
            hasMoved = false;
            
            // Remove transition for immediate visual feedback
            messageDiv.style.transition = 'none';
            
            // Start long-press timer for context menu
            longPressTimer = setTimeout(() => {
                // Only trigger if user hasn't moved significantly
                if (!hasMoved && !isScrolling) {
                    // FIX 10: Vibrate for haptic feedback if supported
                    if (navigator.vibrate) {
                        navigator.vibrate(50);
                    }
                    
                    // Create a synthetic event for context menu positioning
                    const touchEvent = {
                        preventDefault: () => {},
                        clientX: e.touches[0].clientX,
                        clientY: e.touches[0].clientY
                    };
                    this.showContextMenu(touchEvent, message.messageId, isOwn);
                    
                    // Prevent swipe from triggering after long-press
                    isSwiping = false;
                    resetSwipeVisual();
                }
            }, LONG_PRESS_DURATION);
        }, { passive: true });
        
        messageDiv.addEventListener('touchmove', (e) => {
            if (!isSwiping) return;
            
            currentX = e.touches[0].clientX;
            currentY = e.touches[0].clientY;
            
            const deltaX = currentX - startX;
            const deltaY = currentY - startY;
            const absDeltaX = Math.abs(deltaX);
            const absDeltaY = Math.abs(deltaY);
            
            // FIX: Detect if user is scrolling vertically (not swiping)
            if (!hasMoved && absDeltaY > SCROLL_THRESHOLD && absDeltaY > absDeltaX) {
                isScrolling = true;
                cancelLongPress();
                resetSwipeVisual();
                return; // Let the scroll happen naturally
            }
            
            // Any significant movement cancels long-press
            if (absDeltaX > MOVEMENT_THRESHOLD || absDeltaY > MOVEMENT_THRESHOLD) {
                hasMoved = true;
                cancelLongPress();
            }
            
            // Only allow swipe right (positive delta) for reply, and only if not scrolling
            if (!isScrolling && deltaX > MOVEMENT_THRESHOLD && absDeltaY < 30) {
                // Add visual feedback with clamped transform
                const translateX = Math.min(deltaX * 0.6, 60); // Dampen and cap movement
                messageDiv.style.transform = `translateX(${translateX}px)`;
            }
        }, { passive: true });
        
        messageDiv.addEventListener('touchend', (e) => {
            cancelLongPress();
            
            if (!isSwiping) {
                resetSwipeVisual();
                return;
            }
            
            const deltaX = currentX - startX;
            const touchDuration = Date.now() - touchStartTime;
            
            // Reset visual state
            resetSwipeVisual();
            
            // FIX: Only trigger reply if:
            // 1. Not scrolling
            // 2. Swiped far enough
            // 3. Touch was intentional (not too quick, not too slow)
            if (!isScrolling && deltaX > SWIPE_THRESHOLD && touchDuration > 100 && touchDuration < 800) {
                // FIX 10: Haptic feedback for successful swipe
                if (navigator.vibrate) {
                    navigator.vibrate(30);
                }
                this.startReply(message);
            }
            
            // Reset state
            isSwiping = false;
            isScrolling = false;
            hasMoved = false;
            startX = 0;
            startY = 0;
            currentX = 0;
            currentY = 0;
        });
        
        messageDiv.addEventListener('touchcancel', () => {
            cancelLongPress();
            isSwiping = false;
            isScrolling = false;
            hasMoved = false;
            resetSwipeVisual();
        });
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
            // Check if message already exists (for updates)
            if (this.displayedMessageIds.has(msg.messageId)) {
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
     * Open image preview modal
     */
    openImagePreview(url) {
        const modal = document.getElementById('imagePreviewModal');
        const img = document.getElementById('imagePreviewImg');
        if (!modal || !img) return;

        img.src = url;
        modal.style.display = 'flex';

        const close = () => {
            modal.style.display = 'none';
            img.src = '';
            modal.removeEventListener('click', onClick);
            document.removeEventListener('keydown', onKey);
        };

        const onClick = (e) => {
            if (e.target === modal) {
                close();
            }
        };
        const onKey = (e) => {
            if (e.key === 'Escape') {
                close();
            }
        };

        modal.addEventListener('click', onClick);
        document.addEventListener('keydown', onKey);
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
     * Show error message (legacy wrapper using toast system)
     */
    showError(message) {
        this.showToast(message, 'error');
    },
    
    /**
     * FIX 2 & 12: Show context menu for message actions
     * - Properly clamped to viewport bounds (never overflows)
     * - Auto-flips vertically if near bottom
     * - Shifts horizontally if near screen edge
     * - Highlights active message
     * @param {Event} event - The click/right-click event
     * @param {string} messageId - The message ID
     * @param {boolean} isOwnMessage - Whether this is the current user's message
     */
    showContextMenu(event, messageId, isOwnMessage = null) {
        // Close any existing menu and clear previous highlight
        this.closeContextMenu();
        
        // Get message data from DOM if isOwnMessage not provided
        if (isOwnMessage === null) {
            const messageDiv = this.messagesContainer.querySelector(`[data-message-id="${messageId}"]`);
            if (messageDiv) {
                isOwnMessage = messageDiv.dataset.senderId === this.userId;
            }
        }
        
        // Get message data for reply/copy
        const messageDiv = this.messagesContainer.querySelector(`[data-message-id="${messageId}"]`);
        const messageData = this.getMessageDataFromDom(messageDiv);
        
        // FIX 12: Add highlight to active message
        if (messageDiv) {
            messageDiv.classList.add('message-context-active');
            this.activeContextMessageId = messageId;
        }
        
        const menu = document.createElement('div');
        menu.className = 'message-context-menu';
        
        // Build menu items based on ownership
        // Reply & Copy are available for ALL messages
        // Edit & Unsend are ONLY for own messages
        let menuHtml = `
            <button class="context-menu-item" data-action="reply">
                <span class="context-menu-icon">↩️</span>
                <span>Reply</span>
            </button>
            <button class="context-menu-item" data-action="copy">
                <span class="context-menu-icon">📋</span>
                <span>Copy</span>
            </button>
        `;
        
        // Add Edit & Unsend only for own messages
        if (isOwnMessage) {
            const isFileMessage = messageDiv && messageDiv.classList.contains('message-file');
            
            // Only allow edit for text messages, not files
            if (!isFileMessage) {
                menuHtml += `
                    <div class="context-menu-divider"></div>
                    <button class="context-menu-item" data-action="edit">
                        <span class="context-menu-icon">✏️</span>
                        <span>Edit Message</span>
                    </button>
                `;
            }
            
            menuHtml += `
                ${isFileMessage ? '<div class="context-menu-divider"></div>' : ''}
                <button class="context-menu-item context-menu-item-danger" data-action="unsend">
                    <span class="context-menu-icon">🗑️</span>
                    <span>Unsend Message</span>
                </button>
            `;
        }
        
        menu.innerHTML = menuHtml;
        
        document.body.appendChild(menu);
        this.activeContextMenu = menu;
        
        // FIX 2: Better menu positioning with viewport clamping
        // Wait for menu to render to get accurate dimensions
        requestAnimationFrame(() => {
            const menuRect = menu.getBoundingClientRect();
            const menuWidth = menuRect.width || 200;
            const menuHeight = menuRect.height || 200;
            
            // Get touch/click position
            let x = event.clientX || event.pageX || window.innerWidth / 2;
            let y = event.clientY || event.pageY || window.innerHeight / 2;
            
            // Get viewport dimensions
            const viewportWidth = window.innerWidth;
            const viewportHeight = window.innerHeight;
            
            // Get input bar height for bottom constraint
            const inputBarHeight = document.querySelector('.message-input-container')?.offsetHeight || 80;
            const safeAreaBottom = parseInt(getComputedStyle(document.documentElement).getPropertyValue('--safe-area-bottom') || '0') || 0;
            
            // Calculate maximum allowed Y position
            const maxY = viewportHeight - inputBarHeight - safeAreaBottom - 10;
            const minY = 10;
            const minX = 10;
            const maxX = viewportWidth - menuWidth - 10;
            
            // FIX 2: Auto-flip vertically if near bottom
            if (y + menuHeight > maxY) {
                // Try to flip above the touch point
                const flippedY = y - menuHeight - 10;
                if (flippedY >= minY) {
                    y = flippedY;
                } else {
                    // Clamp to maximum visible area
                    y = Math.max(minY, maxY - menuHeight);
                }
            }
            
            // FIX 2: Shift horizontally if near screen edge
            if (x + menuWidth > maxX + menuWidth) {
                x = maxX;
            }
            if (x < minX) {
                x = minX;
            }
            
            // Final clamp to ensure menu is fully visible
            x = Math.max(minX, Math.min(x, maxX));
            y = Math.max(minY, Math.min(y, maxY - menuHeight));
            
            menu.style.left = x + 'px';
            menu.style.top = y + 'px';
        });
        
        // Add click handlers for menu items
        menu.querySelectorAll('.context-menu-item').forEach(item => {
            item.addEventListener('click', (e) => {
                const action = item.dataset.action;
                this.handleMessageAction(action, messageId, messageData);
                this.closeContextMenu();
            });
        });
    },
    
    /**
     * Extract message data from DOM element
     * FIX: Properly get sender name even when message header is hidden (grouped messages)
     */
    getMessageDataFromDom(messageDiv) {
        if (!messageDiv) return null;
        
        const senderId = messageDiv.dataset.senderId;
        const isOwnMessage = senderId === this.userId;
        
        // Get sender name from data attribute first, then fallback to header text
        let senderName = messageDiv.dataset.senderName 
            || messageDiv.querySelector('.message-sender')?.textContent 
            || (isOwnMessage ? this.userName : 'Unknown');
        
        return {
            messageId: messageDiv.dataset.messageId,
            senderId: senderId,
            senderName: senderName,
            content: messageDiv.dataset.originalContent || '',
            type: messageDiv.classList.contains('message-file') ? 'FILE' : 'TEXT'
        };
    },
    
    /**
     * FIX 12: Close the context menu and clear active message highlight
     */
    closeContextMenu() {
        if (this.activeContextMenu) {
            this.activeContextMenu.remove();
            this.activeContextMenu = null;
        }
        
        // FIX 12: Clear active message highlight
        if (this.activeContextMessageId) {
            const messageDiv = this.messagesContainer?.querySelector(`[data-message-id="${this.activeContextMessageId}"]`);
            if (messageDiv) {
                messageDiv.classList.remove('message-context-active');
            }
            this.activeContextMessageId = null;
        }
    },
    
    /**
     * Handle message action (reply, copy, edit, or unsend)
     */
    handleMessageAction(action, messageId, messageData) {
        switch (action) {
            case 'reply':
                this.startReply(messageData);
                break;
            case 'copy':
                this.copyMessageContent(messageData);
                break;
            case 'edit':
                this.startEditMessage(messageId);
                break;
            case 'unsend':
                this.unsendMessage(messageId);
                break;
        }
    },
    
    /**
     * FIX 8 & 10: Start replying to a message
     * Uses wrapper for smooth height animation to prevent scroll jump
     * Shows visual feedback toast
     * FIX: Shows 'yourself' when replying to own message
     */
    startReply(message) {
        if (!message) return;
        
        // Store the message being replied to
        this.replyToMessage = message;
        
        // Determine display name - show 'yourself' for own messages
        const isOwnMessage = message.senderId === this.userId;
        const displayName = isOwnMessage ? 'yourself' : (message.senderName || 'Unknown');
        
        // Show reply preview bar
        const previewBar = document.getElementById('replyPreviewBar');
        const senderEl = document.getElementById('replyPreviewSender');
        const textEl = document.getElementById('replyPreviewText');
        const iconEl = document.getElementById('replyPreviewTypeIcon');
        
        if (previewBar && senderEl && textEl && iconEl) {
            // Set sender name
            senderEl.textContent = `Replying to ${displayName}`;
            
            // Set preview text (truncated)
            let previewText = message.content || '';
            if (previewText.length > 60) {
                previewText = previewText.substring(0, 60) + '...';
            }
            textEl.textContent = previewText;
            
            // FIX 5: Set type icon for visual clarity
            iconEl.textContent = this.getMessageTypeIcon(message.type);
            
            // FIX 8: Show with smooth animation using wrapper
            if (this.replyPreviewWrapper) {
                this.replyPreviewWrapper.classList.add('visible');
            } else {
                // Fallback if wrapper not setup
                previewBar.style.display = 'flex';
            }
        }
        
        // FIX 10: Show visual feedback
        this.showToast(`Replying to ${displayName}`, 'info');
        
        // Focus input
        if (this.messageInput) {
            this.messageInput.focus();
        }
    },
    
    /**
     * FIX 8: Cancel reply mode with smooth animation
     */
    cancelReply() {
        this.replyToMessage = null;
        
        // FIX 8: Hide with smooth animation using wrapper
        if (this.replyPreviewWrapper) {
            this.replyPreviewWrapper.classList.remove('visible');
        } else {
            // Fallback if wrapper not setup
            const previewBar = document.getElementById('replyPreviewBar');
            if (previewBar) {
                previewBar.style.display = 'none';
            }
        }
    },
    
    /**
     * FIX 10: Copy message content to clipboard with visual feedback
     */
    async copyMessageContent(message) {
        if (!message || !message.content) {
            this.showToast('No content to copy', 'error');
            return;
        }
        
        try {
            // Use clipboard API with proper escaping
            await navigator.clipboard.writeText(message.content);
            
            // FIX 10: Haptic feedback on mobile
            if (navigator.vibrate) {
                navigator.vibrate(30);
            }
            
            // FIX 10: Show success toast
            this.showToast('Copied to clipboard', 'success');
        } catch (error) {
            console.error('Failed to copy:', error);
            
            // Fallback for older browsers
            const textArea = document.createElement('textarea');
            textArea.value = message.content;
            textArea.style.position = 'fixed';
            textArea.style.left = '-9999px';
            document.body.appendChild(textArea);
            textArea.select();
            
            try {
                document.execCommand('copy');
                this.showToast('Copied to clipboard', 'success');
            } catch (e) {
                this.showToast('Failed to copy message', 'error');
            }
            
            document.body.removeChild(textArea);
        }
    },
    
    /**
     * FIX 10: Show toast notification with animation
     * @param {string} message - Toast message
     * @param {string} type - 'success', 'error', or 'info'
     */
    showToast(message, type = 'info') {
        // Remove existing toast if any
        if (this.activeToast) {
            this.activeToast.remove();
            this.activeToast = null;
        }
        
        const toast = document.createElement('div');
        toast.className = `toast-notification toast-${type}`;
        toast.textContent = message;
        document.body.appendChild(toast);
        this.activeToast = toast;
        
        // Trigger animation
        requestAnimationFrame(() => {
            toast.classList.add('visible');
        });
        
        // Auto-dismiss
        setTimeout(() => {
            toast.classList.remove('visible');
            setTimeout(() => {
                if (toast.parentNode) {
                    toast.remove();
                }
                if (this.activeToast === toast) {
                    this.activeToast = null;
                }
            }, 300);
        }, 2000);
    },
    
    /**
     * Show success toast message (legacy wrapper)
     */
    showSuccess(message) {
        this.showToast(message, 'success');
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
        this.cancelReply();
        this.cancelEdit();
        
        // Hide scroll-to-bottom button
        if (this.scrollToBottomBtn) {
            this.scrollToBottomBtn.style.display = 'none';
            this.scrollToBottomBtn.classList.remove('visible');
        }
        this.unreadCount = 0;
        
        // FIX: Reset message grouping state
        this.lastRenderedSenderId = null;
        
        // FIX: Clear any active toast
        if (this.activeToast) {
            this.activeToast.remove();
            this.activeToast = null;
        }
        
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
