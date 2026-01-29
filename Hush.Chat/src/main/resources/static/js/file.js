// File Upload and Download Module

const fileHandler = {
    // Configuration (loaded from server)
    maxFileSizeBytes: 200 * 1024 * 1024, // Default 200MB
    allowedExtensions: [],
    
    // State
    uploadQueue: [],
    isUploading: false,
    
    // UI Elements
    fileInput: null,
    uploadProgress: null,
    
    /**
     * Initialize file handler
     */
    async init() {
        // Load config from server
        await this.loadConfig();
        
        // Create hidden file input
        this.createFileInput();
        
        // Setup drag and drop
        this.setupDragAndDrop();
        
        console.log('FileHandler initialized - Max size:', this.formatFileSize(this.maxFileSizeBytes));
    },
    
    /**
     * Load upload configuration from server
     */
    async loadConfig() {
        try {
            const response = await api.get('/files/config');
            if (response.success && response.data) {
                this.maxFileSizeBytes = response.data.maxFileSizeBytes || this.maxFileSizeBytes;
                this.allowedExtensions = response.data.allowedExtensions || [];
            }
        } catch (error) {
            console.warn('Failed to load file config, using defaults:', error);
        }
    },
    
    /**
     * Create hidden file input element
     */
    createFileInput() {
        this.fileInput = document.getElementById('fileInput');
        if (!this.fileInput) {
            this.fileInput = document.createElement('input');
            this.fileInput.type = 'file';
            this.fileInput.id = 'fileInput';
            this.fileInput.multiple = true;
            this.fileInput.style.display = 'none';
            document.body.appendChild(this.fileInput);
        }
        
        this.fileInput.addEventListener('change', (e) => {
            if (e.target.files && e.target.files.length > 0) {
                this.handleFileSelection(Array.from(e.target.files));
            }
            // Reset input so same file can be selected again
            e.target.value = '';
        });
    },
    
    /**
     * Open file picker dialog
     */
    openFilePicker() {
        if (this.fileInput) {
            this.fileInput.click();
        }
    },
    
    /**
     * Setup drag and drop on messages container
     */
    setupDragAndDrop() {
        const messagesContainer = document.getElementById('messagesContainer');
        if (!messagesContainer) return;
        
        // Prevent default drag behaviors
        ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(eventName => {
            messagesContainer.addEventListener(eventName, (e) => {
                e.preventDefault();
                e.stopPropagation();
            });
        });
        
        // Highlight on drag enter
        ['dragenter', 'dragover'].forEach(eventName => {
            messagesContainer.addEventListener(eventName, () => {
                messagesContainer.classList.add('drag-over');
            });
        });
        
        // Remove highlight on drag leave/drop
        ['dragleave', 'drop'].forEach(eventName => {
            messagesContainer.addEventListener(eventName, () => {
                messagesContainer.classList.remove('drag-over');
            });
        });
        
        // Handle drop
        messagesContainer.addEventListener('drop', (e) => {
            const files = e.dataTransfer.files;
            if (files && files.length > 0) {
                this.handleFileSelection(Array.from(files));
            }
        });
    },
    
    /**
     * Handle file selection (from picker or drop)
     */
    handleFileSelection(files) {
        if (!files || files.length === 0) return;
        
        const validFiles = [];
        const errors = [];
        
        for (const file of files) {
            const validation = this.validateFile(file);
            if (validation.valid) {
                validFiles.push(file);
            } else {
                errors.push(`${file.name}: ${validation.error}`);
            }
        }
        
        // Show errors if any
        if (errors.length > 0) {
            console.warn('File validation errors:', errors);
            if (typeof chat !== 'undefined' && chat.showError) {
                chat.showError(errors.join('\n'));
            }
        }
        
        // Upload valid files
        if (validFiles.length > 0) {
            this.uploadFiles(validFiles);
        }
    },
    
    /**
     * Validate a single file
     */
    validateFile(file) {
        // Check file size
        if (file.size > this.maxFileSizeBytes) {
            return {
                valid: false,
                error: `File too large (max ${this.formatFileSize(this.maxFileSizeBytes)})`
            };
        }
        
        // Check extension (if restrictions configured)
        if (this.allowedExtensions.length > 0) {
            const ext = this.getFileExtension(file.name);
            if (!this.allowedExtensions.includes(ext.toLowerCase())) {
                return {
                    valid: false,
                    error: `File type not allowed: .${ext}`
                };
            }
        }
        
        return { valid: true };
    },
    
    /**
     * Upload files to server
     */
    async uploadFiles(files) {
        if (this.isUploading) {
            // Queue files if already uploading
            this.uploadQueue.push(...files);
            return;
        }
        
        this.isUploading = true;
        
        const roomCode = localStorage.getItem('roomCode');
        const userId = localStorage.getItem('userId');
        
        if (!roomCode || !userId) {
            console.error('Missing room or user info');
            this.isUploading = false;
            return;
        }
        
        // Show upload progress UI
        this.showUploadProgress(files.length);
        
        try {
            const formData = new FormData();
            formData.append('roomCode', roomCode);
            formData.append('userId', userId);
            
            for (const file of files) {
                formData.append('files', file);
            }
            
            const response = await this.uploadWithProgress(formData);
            
            if (response.success && response.data) {
                // Render file messages
                for (const fileData of response.data) {
                    this.renderFileMessage(fileData, true);
                }
            } else {
                throw new Error(response.error || 'Upload failed');
            }
        } catch (error) {
            console.error('File upload failed:', error);
            if (typeof chat !== 'undefined' && chat.showError) {
                chat.showError('Failed to upload file(s): ' + error.message);
            }
        } finally {
            this.hideUploadProgress();
            this.isUploading = false;

            // Process queued files
            if (this.uploadQueue.length > 0) {
                const queuedFiles = this.uploadQueue.splice(0);
                this.uploadFiles(queuedFiles);
            }
        }
    },

    /**
     * Upload with progress tracking
     */
    async uploadWithProgress(formData) {
        return new Promise((resolve, reject) => {
            const xhr = new XMLHttpRequest();

            xhr.upload.addEventListener('progress', (e) => {
                if (e.lengthComputable) {
                    const percent = Math.round((e.loaded / e.total) * 100);
                    this.updateUploadProgress(percent);
                }
            });

            xhr.addEventListener('load', () => {
                if (xhr.status >= 200 && xhr.status < 300) {
                    try {
                        resolve(JSON.parse(xhr.responseText));
                    } catch (e) {
                        reject(new Error('Invalid response'));
                    }
                } else {
                    try {
                        const error = JSON.parse(xhr.responseText);
                        reject(new Error(error.error || 'Upload failed'));
                    } catch (e) {
                        reject(new Error(`Upload failed: ${xhr.status}`));
                    }
                }
            });

            xhr.addEventListener('error', () => {
                reject(new Error('Network error'));
            });

            xhr.addEventListener('timeout', () => {
                reject(new Error('Upload timed out'));
            });

            xhr.open('POST', `${config.API_BASE_URL}/files/upload`);
            xhr.timeout = 5 * 60 * 1000; // 5 minutes for large files
            xhr.send(formData);
        });
    },

    /**
     * Show upload progress UI
     */
    showUploadProgress(fileCount) {
        // Remove existing progress if any
        this.hideUploadProgress();

        const progressHtml = `
            <div id="uploadProgressContainer" class="upload-progress-container">
                <div class="upload-progress-bar">
                    <div class="upload-progress-fill" id="uploadProgressFill"></div>
                </div>
                <span class="upload-progress-text" id="uploadProgressText">
                    Uploading ${fileCount} file${fileCount > 1 ? 's' : ''}... 0%
                </span>
            </div>
        `;

        const inputContainer = document.querySelector('.message-input-container');
        if (inputContainer) {
            inputContainer.insertAdjacentHTML('beforebegin', progressHtml);
        }
    },

    /**
     * Update upload progress
     */
    updateUploadProgress(percent) {
        const fill = document.getElementById('uploadProgressFill');
        const text = document.getElementById('uploadProgressText');

        if (fill) {
            fill.style.width = percent + '%';
        }
        if (text) {
            text.textContent = `Uploading... ${percent}%`;
        }
    },

    /**
     * Hide upload progress UI
     */
    hideUploadProgress() {
        const container = document.getElementById('uploadProgressContainer');
        if (container) {
            container.remove();
        }
    },

    /**
     * Render a file message in chat
     */
    renderFileMessage(fileData, isOwn = false) {
        if (!fileData) return;

        const messagesContainer = document.getElementById('messagesContainer');
        if (!messagesContainer) return;

        // Normalize field names (handle both upload response and message formats)
        const fileId = fileData.fileId;
        const filename = fileData.originalFilename || fileData.fileName;
        const contentType = fileData.contentType || fileData.fileContentType;
        const fileSize = fileData.fileSize;
        const uploaderName = fileData.uploaderName;
        const uploadTime = fileData.uploadTime;
        const expiryTime = fileData.expiryTime;

        // Check if message already exists
        if (typeof chat !== 'undefined' && chat.displayedMessageIds) {
            const messageKey = 'file_' + fileId;
            if (chat.displayedMessageIds.has(messageKey)) {
                return;
            }
            chat.displayedMessageIds.add(messageKey);
        }

        const isImage = this.isImageFile(contentType);
        const timestamp = new Date(uploadTime);
        const timeString = timestamp.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

        // Calculate expiry
        const expiryTimeObj = expiryTime ? new Date(expiryTime) : null;
        const remainingMs = expiryTimeObj ? expiryTimeObj - new Date() : 10 * 60 * 1000;
        const remainingSeconds = Math.max(0, Math.floor(remainingMs / 1000));
        const minutes = Math.floor(remainingSeconds / 60);
        const seconds = remainingSeconds % 60;
        const expiryDisplay = `${minutes}:${seconds.toString().padStart(2, '0')}`;

        // Urgency class
        let urgencyClass = '';
        if (remainingSeconds <= 60) {
            urgencyClass = 'expiry-critical';
        } else if (remainingSeconds <= 180) {
            urgencyClass = 'expiry-warning';
        }

        const messageDiv = document.createElement('div');
        messageDiv.className = `message message-file ${isOwn ? 'message-own' : 'message-other'}`;
        messageDiv.dataset.messageId = 'file_' + fileId;
        messageDiv.dataset.fileId = fileId;

        if (expiryTime) {
            messageDiv.dataset.expiryTime = expiryTime;
        }

        const senderName = isOwn ? 'You' : (uploaderName || 'Unknown');
        const fileIcon = this.getFileIcon(contentType, filename);
        const fileSizeStr = this.formatFileSize(fileSize);
        const previewUrl = `${config.API_BASE_URL}/files/preview/${fileId}?userId=${localStorage.getItem('userId')}`;
        const downloadUrl = `${config.API_BASE_URL}/files/download/${fileId}?userId=${localStorage.getItem('userId')}`;

        let contentHtml;
        if (isImage) {
            contentHtml = `
                <div class="file-preview-container">
                    <img src="${previewUrl}" alt="${this.escapeHtml(filename)}"
                         class="file-preview-image" loading="lazy"
                         onclick="fileHandler.downloadFile('${fileId}')">
                </div>
                <div class="file-info">
                    <span class="file-name">${this.escapeHtml(filename)}</span>
                    <span class="file-size">${fileSizeStr}</span>
                </div>
            `;
        } else {
            contentHtml = `
                <div class="file-content" onclick="fileHandler.downloadFile('${fileId}')">
                    <span class="file-icon">${fileIcon}</span>
                    <div class="file-details">
                        <span class="file-name">${this.escapeHtml(filename)}</span>
                        <span class="file-size">${fileSizeStr}</span>
                    </div>
                    <span class="file-download-icon">⬇️</span>
                </div>
            `;
        }

        messageDiv.innerHTML = `
            <div class="message-header">
                <span class="message-sender">${this.escapeHtml(senderName)}</span>
                <div class="message-meta">
                    <span class="expiry-badge ${urgencyClass}">⏱ ${expiryDisplay}</span>
                    <span class="message-time">${timeString}</span>
                </div>
            </div>
            <div class="message-content file-message-content">
                ${contentHtml}
            </div>
        `;

        messagesContainer.appendChild(messageDiv);

        // Scroll to bottom if this is own message
        if (isOwn && typeof chat !== 'undefined' && chat.forceScrollToBottom) {
            chat.forceScrollToBottom();
        } else if (typeof chat !== 'undefined' && chat.scrollToBottom) {
            chat.scrollToBottom();
        }
    },

    /**
     * Download a file
     */
    async downloadFile(fileId) {
        const userId = localStorage.getItem('userId');
        if (!userId) return;

        const downloadUrl = `${config.API_BASE_URL}/files/download/${fileId}?userId=${userId}`;

        try {
            // Show loading indicator on the file message
            const messageDiv = document.querySelector(`[data-file-id="${fileId}"]`);
            if (messageDiv) {
                messageDiv.classList.add('file-downloading');
            }

            // Fetch file
            const response = await fetch(downloadUrl);

            if (!response.ok) {
                if (response.status === 404) {
                    throw new Error('File has expired or been deleted');
                }
                throw new Error('Download failed');
            }

            // Get filename from Content-Disposition header
            const disposition = response.headers.get('Content-Disposition');
            let filename = 'download';
            if (disposition) {
                const match = disposition.match(/filename="(.+?)"/);
                if (match) {
                    filename = match[1];
                }
            }

            // Create blob and download
            const blob = await response.blob();
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = filename;
            document.body.appendChild(a);
            a.click();
            window.URL.revokeObjectURL(url);
            a.remove();

        } catch (error) {
            console.error('Download failed:', error);
            if (typeof chat !== 'undefined' && chat.showError) {
                chat.showError(error.message || 'Failed to download file');
            }
        } finally {
            // Remove loading indicator
            const messageDiv = document.querySelector(`[data-file-id="${fileId}"]`);
            if (messageDiv) {
                messageDiv.classList.remove('file-downloading');
            }
        }
    },

    /**
     * Check if file is an image
     */
    isImageFile(contentType) {
        return contentType && contentType.startsWith('image/');
    },

    /**
     * Get file icon based on type
     */
    getFileIcon(contentType, filename) {
        if (!contentType) {
            const ext = this.getFileExtension(filename);
            contentType = this.getContentTypeFromExtension(ext);
        }

        if (contentType.startsWith('image/')) return '🖼️';
        if (contentType.startsWith('video/')) return '🎬';
        if (contentType.startsWith('audio/')) return '🎵';
        if (contentType.includes('pdf')) return '📕';
        if (contentType.includes('word') || contentType.includes('document')) return '📝';
        if (contentType.includes('excel') || contentType.includes('spreadsheet')) return '📊';
        if (contentType.includes('powerpoint') || contentType.includes('presentation')) return '📽️';
        if (contentType.includes('zip') || contentType.includes('archive') || contentType.includes('compressed')) return '📦';
        if (contentType.includes('text')) return '📄';

        return '📎';
    },

    /**
     * Get content type from extension
     */
    getContentTypeFromExtension(ext) {
        const types = {
            'jpg': 'image/jpeg',
            'jpeg': 'image/jpeg',
            'png': 'image/png',
            'gif': 'image/gif',
            'webp': 'image/webp',
            'pdf': 'application/pdf',
            'doc': 'application/msword',
            'docx': 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
            'xls': 'application/vnd.ms-excel',
            'xlsx': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
            'ppt': 'application/vnd.ms-powerpoint',
            'pptx': 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
            'zip': 'application/zip',
            'rar': 'application/x-rar-compressed',
            '7z': 'application/x-7z-compressed',
            'txt': 'text/plain',
            'mp3': 'audio/mpeg',
            'mp4': 'video/mp4',
            'mov': 'video/quicktime',
            'avi': 'video/x-msvideo'
        };
        return types[ext.toLowerCase()] || 'application/octet-stream';
    },

    /**
     * Get file extension from filename
     */
    getFileExtension(filename) {
        if (!filename) return '';
        const lastDot = filename.lastIndexOf('.');
        return lastDot >= 0 ? filename.substring(lastDot + 1) : '';
    },

    /**
     * Format file size for display
     */
    formatFileSize(bytes) {
        if (bytes < 1024) {
            return bytes + ' B';
        } else if (bytes < 1024 * 1024) {
            return (bytes / 1024).toFixed(1) + ' KB';
        } else if (bytes < 1024 * 1024 * 1024) {
            return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
        } else {
            return (bytes / (1024 * 1024 * 1024)).toFixed(1) + ' GB';
        }
    },

    /**
     * Escape HTML to prevent XSS
     */
    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
};

// Initialize when DOM is ready
document.addEventListener('DOMContentLoaded', () => {
    fileHandler.init();
});
