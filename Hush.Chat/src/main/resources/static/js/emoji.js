/**
 * Emoji System - Instagram/WhatsApp style emoji support
 * Handles emoji picker, quick reactions, and message reactions
 */

const emojiSystem = {
    // Configuration
    DEFAULT_REACTIONS: ['❤️', '😂', '😮', '😢', '😡', '👍'],
    RECENT_EMOJIS_KEY: 'chat_recent_emojis',
    MAX_RECENT_EMOJIS: 20,
    
    // State
    emojiPickerModal: null,
    emojiPickerElement: null,
    isPickerOpen: false,
    pickerContext: null, // 'input' or { type: 'reaction', messageId: '...' }
    
    /**
     * Initialize the emoji system
     */
    init() {
        this.emojiPickerModal = document.getElementById('emojiPickerModal');
        this.emojiPickerElement = document.querySelector('emoji-picker');
        
        this.setupEmojiPickerButton();
        this.setupEmojiPickerModal();
        this.setupEmojiPickerEvents();
        
        console.log('Emoji system initialized');
    },
    
    /**
     * Setup emoji picker button in message input
     */
    setupEmojiPickerButton() {
        const emojiBtn = document.getElementById('emojiPickerBtn');
        if (emojiBtn) {
            emojiBtn.addEventListener('click', (e) => {
                e.preventDefault();
                e.stopPropagation();
                this.openPicker('input');
            });
        }
    },
    
    /**
     * Setup emoji picker modal interactions
     */
    setupEmojiPickerModal() {
        if (!this.emojiPickerModal) return;
        
        // Close on backdrop click
        const backdrop = this.emojiPickerModal.querySelector('.emoji-picker-backdrop');
        if (backdrop) {
            backdrop.addEventListener('click', () => {
                this.closePicker();
            });
        }
        
        // Close on Escape key
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && this.isPickerOpen) {
                this.closePicker();
            }
        });
    },
    
    /**
     * Setup emoji picker element events
     */
    setupEmojiPickerEvents() {
        if (!this.emojiPickerElement) return;
        
        // Handle emoji selection
        this.emojiPickerElement.addEventListener('emoji-click', (event) => {
            const emoji = event.detail.unicode;
            this.handleEmojiSelected(emoji);
        });
    },
    
    /**
     * Open the emoji picker
     * @param {string|object} context - 'input' for message input, or { type: 'reaction', messageId: '...' }
     */
    openPicker(context) {
        if (!this.emojiPickerModal) return;
        
        this.pickerContext = context;
        this.isPickerOpen = true;
        this.emojiPickerModal.style.display = 'flex';
        
        // Apply recent emojis to picker if supported
        this.updatePickerRecentEmojis();
    },
    
    /**
     * Close the emoji picker
     */
    closePicker() {
        if (!this.emojiPickerModal) return;
        
        this.isPickerOpen = false;
        this.emojiPickerModal.style.display = 'none';
        this.pickerContext = null;
    },
    
    /**
     * Handle emoji selection from picker
     */
    handleEmojiSelected(emoji) {
        if (this.pickerContext === 'input') {
            // Insert emoji into message input at cursor position
            this.insertEmojiAtCursor(emoji);
        } else if (this.pickerContext && this.pickerContext.type === 'reaction') {
            // Add reaction to message
            this.addReaction(this.pickerContext.messageId, emoji);
        }
        
        // Save to recent emojis
        this.saveRecentEmoji(emoji);
        
        // Close picker
        this.closePicker();
    },
    
    /**
     * Insert emoji at cursor position in message input
     * Properly handles multi-byte emoji characters
     */
    insertEmojiAtCursor(emoji) {
        const textarea = document.getElementById('messageInput');
        if (!textarea) return;
        
        const start = textarea.selectionStart;
        const end = textarea.selectionEnd;
        const value = textarea.value;
        
        // Insert emoji at cursor position
        textarea.value = value.slice(0, start) + emoji + value.slice(end);
        
        // Move cursor after emoji
        const newPosition = start + emoji.length;
        textarea.setSelectionRange(newPosition, newPosition);
        
        // Focus textarea
        textarea.focus();
        
        // Trigger auto-grow
        if (typeof chat !== 'undefined' && chat.autoGrowTextarea) {
            chat.autoGrowTextarea();
        }
    },
    
    /**
     * Save emoji to recent emojis list
     */
    saveRecentEmoji(emoji) {
        let recent = this.getRecentEmojis();
        
        // Remove if already exists (to move to front)
        recent = recent.filter(e => e !== emoji);
        
        // Add to front
        recent.unshift(emoji);
        
        // Trim to max size
        if (recent.length > this.MAX_RECENT_EMOJIS) {
            recent = recent.slice(0, this.MAX_RECENT_EMOJIS);
        }
        
        // Save to localStorage
        try {
            localStorage.setItem(this.RECENT_EMOJIS_KEY, JSON.stringify(recent));
        } catch (e) {
            console.warn('Failed to save recent emojis:', e);
        }
    },
    
    /**
     * Get recent emojis from localStorage
     */
    getRecentEmojis() {
        try {
            const stored = localStorage.getItem(this.RECENT_EMOJIS_KEY);
            if (stored) {
                return JSON.parse(stored);
            }
        } catch (e) {
            console.warn('Failed to load recent emojis:', e);
        }
        return [];
    },
    
    /**
     * Update picker with recent emojis (if API supports it)
     */
    updatePickerRecentEmojis() {
        // emoji-picker-element stores recent emojis automatically via IndexedDB
        // This is just for reference if we need to manually set custom data sources
    },
    
    /**
     * Create quick reactions bar HTML
     * @returns {string} HTML string
     */
    createQuickReactionsBarHtml() {
        const reactions = this.DEFAULT_REACTIONS;
        let html = '<div class="quick-reactions-bar">';
        
        reactions.forEach(emoji => {
            html += `<button class="reaction-btn" data-emoji="${emoji}" title="React with ${emoji}">${emoji}</button>`;
        });
        
        html += '<button class="reaction-more-btn" title="More reactions">+</button>';
        html += '</div>';
        
        return html;
    },
    
    /**
     * Add reaction to a message
     */
    async addReaction(messageId, emoji) {
        const roomCode = localStorage.getItem('roomCode');
        const userId = localStorage.getItem('userId');
        
        if (!roomCode || !userId) {
            console.error('Missing room or user info for reaction');
            return;
        }
        
        try {
            const response = await api.post('/reactions/toggle', {
                roomCode: roomCode,
                messageId: messageId,
                userId: userId,
                emoji: emoji
            });
            
            if (response.success) {
                const action = response.data.action;
                
                // Update UI
                this.updateMessageReactions(messageId, emoji, action, userId);
                
                // Save to recent emojis
                if (action === 'added') {
                    this.saveRecentEmoji(emoji);
                }
                
                // Show feedback
                if (typeof chat !== 'undefined' && chat.showToast) {
                    chat.showToast(action === 'added' ? 'Reaction added' : 'Reaction removed', 'success');
                }
            }
        } catch (error) {
            console.error('Failed to toggle reaction:', error);
            if (typeof chat !== 'undefined' && chat.showToast) {
                chat.showToast('Failed to add reaction', 'error');
            }
        }
    },
    
    /**
     * Update message reactions in the DOM
     */
    updateMessageReactions(messageId, emoji, action, userId) {
        const messageDiv = document.querySelector(`[data-message-id="${messageId}"]`);
        if (!messageDiv) return;
        
        // Get or find the reactions container (may already exist from rendering)
        let reactionsContainer = messageDiv.querySelector('.message-reactions');
        
        // Create reactions container if it doesn't exist
        if (!reactionsContainer) {
            if (action === 'removed') return; // Nothing to remove
            
            reactionsContainer = document.createElement('div');
            reactionsContainer.className = 'message-reactions';
            reactionsContainer.dataset.messageId = messageId;
            
            // Insert before message footer
            const footer = messageDiv.querySelector('.message-footer');
            if (footer) {
                footer.parentNode.insertBefore(reactionsContainer, footer);
            } else {
                messageDiv.appendChild(reactionsContainer);
            }
        }
        
        // Find or create the reaction badge for this emoji
        let badge = reactionsContainer.querySelector(`[data-emoji="${emoji}"]`);
        
        if (action === 'added') {
            if (badge) {
                // Update existing badge
                const countEl = badge.querySelector('.reaction-count');
                let count = parseInt(countEl.textContent) || 0;
                count++;
                countEl.textContent = count;
                badge.classList.add('active');
            } else {
                // Create new badge
                badge = document.createElement('button');
                badge.className = 'reaction-badge active';
                badge.dataset.emoji = emoji;
                badge.innerHTML = `
                    <span class="reaction-emoji">${emoji}</span>
                    <span class="reaction-count">1</span>
                `;
                
                // Add click handler to toggle
                badge.addEventListener('click', () => {
                    this.addReaction(messageId, emoji);
                });
                
                reactionsContainer.appendChild(badge);
            }
            // Show container
            reactionsContainer.style.display = '';
        } else if (action === 'removed') {
            if (badge) {
                const countEl = badge.querySelector('.reaction-count');
                let count = parseInt(countEl.textContent) || 0;
                count--;
                
                if (count <= 0) {
                    // Remove badge
                    badge.remove();
                    
                    // Hide container if empty
                    if (reactionsContainer.querySelectorAll('.reaction-badge').length === 0) {
                        reactionsContainer.style.display = 'none';
                    }
                } else {
                    countEl.textContent = count;
                    badge.classList.remove('active');
                }
            }
        }
    },
    
    /**
     * Render reactions for a message from API data
     */
    renderMessageReactions(messageDiv, reactions, currentUserId) {
        if (!reactions || Object.keys(reactions).length === 0) {
            // Hide empty reactions container
            const existingContainer = messageDiv.querySelector('.message-reactions');
            if (existingContainer) {
                existingContainer.style.display = 'none';
            }
            return;
        }
        
        let reactionsContainer = messageDiv.querySelector('.message-reactions');
        
        if (!reactionsContainer) {
            reactionsContainer = document.createElement('div');
            reactionsContainer.className = 'message-reactions';
            
            const footer = messageDiv.querySelector('.message-footer');
            if (footer) {
                footer.parentNode.insertBefore(reactionsContainer, footer);
            } else {
                messageDiv.appendChild(reactionsContainer);
            }
        } else {
            // Clear existing badges (keep container)
            reactionsContainer.innerHTML = '';
        }
        
        const messageId = messageDiv.dataset.messageId;
        
        // Sort reactions by count (descending)
        const sortedReactions = Object.entries(reactions).sort((a, b) => b[1].count - a[1].count);
        
        for (const [emoji, data] of sortedReactions) {
            const isActive = data.userIds && data.userIds.includes(currentUserId);
            
            const badge = document.createElement('button');
            badge.className = `reaction-badge${isActive ? ' active' : ''}`;
            badge.dataset.emoji = emoji;
            badge.title = data.userNames ? data.userNames.join(', ') : '';
            badge.innerHTML = `
                <span class="reaction-emoji">${emoji}</span>
                <span class="reaction-count">${data.count}</span>
            `;
            
            badge.addEventListener('click', () => {
                this.addReaction(messageId, emoji);
            });
            
            reactionsContainer.appendChild(badge);
        }
        
        // Show container if it has content
        reactionsContainer.style.display = '';
    },
    
    /**
     * Fetch and render reactions for a list of messages
     */
    async fetchAndRenderReactions(messageIds) {
        if (!messageIds || messageIds.length === 0) return;
        
        const roomCode = localStorage.getItem('roomCode');
        const userId = localStorage.getItem('userId');
        
        if (!roomCode) return;
        
        try {
            const response = await api.post(`/reactions/batch?roomCode=${roomCode}`, messageIds);
            
            if (response.success && response.data) {
                for (const [messageId, summary] of Object.entries(response.data)) {
                    const messageDiv = document.querySelector(`[data-message-id="${messageId}"]`);
                    if (messageDiv && summary.reactions) {
                        this.renderMessageReactions(messageDiv, summary.reactions, userId);
                    }
                }
            }
        } catch (error) {
            // Silently ignore reaction fetch errors - non-critical feature
            console.warn('Failed to fetch reactions (non-critical):', error.message);
        }
    }
};

// Initialize when DOM is ready
document.addEventListener('DOMContentLoaded', () => {
    emojiSystem.init();
});
