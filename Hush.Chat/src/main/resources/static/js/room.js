// Room Management Logic

// Create Room
document.getElementById('createRoomForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    
    const roomName = document.getElementById('roomName').value.trim();
    const userName = document.getElementById('creatorName').value.trim();
    const createBtn = document.getElementById('createRoomBtn');
    
    createBtn.disabled = true;
    createBtn.textContent = 'Creating...';
    
    try {
        const response = await api.post('/rooms/create', {
            roomName: roomName,
            userName: userName
        });
        
        if (response.success) {
            const data = response.data;
            
            // Store room and user info in localStorage
            localStorage.setItem('roomCode', data.roomCode);
            localStorage.setItem('roomName', data.roomName);
            localStorage.setItem('userId', data.userId);
            localStorage.setItem('userName', data.userName);
            localStorage.setItem('isCreator', 'true');
            // CRITICAL: Store JWT token for WebSocket authentication
            localStorage.setItem('sessionToken', data.token);
            localStorage.setItem('token', data.token);  // Backup key
            
            // Show success with room code
            showSuccess(`Room created! Code: ${data.roomCode}`);
            
            // Redirect to chat after 1.5 seconds
            setTimeout(() => {
                window.location.href = 'chat.html';
            }, 1500);
        } else {
            throw new Error(response.error || 'Failed to create room');
        }
    } catch (error) {
        showError(error.message || 'Failed to create room');
        createBtn.disabled = false;
        createBtn.textContent = 'Create Room';
    }
});

// Join Room
document.getElementById('joinRoomForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    
    const roomCode = document.getElementById('roomCode').value.trim().toUpperCase();
    const userName = document.getElementById('joinerName').value.trim();
    const joinBtn = document.getElementById('joinRoomBtn');
    
    joinBtn.disabled = true;
    joinBtn.textContent = 'Joining...';
    
    try {
        const response = await api.post('/rooms/join', {
            roomCode: roomCode,
            userName: userName,
            userId: localStorage.getItem('userId') || null
        });
        
        if (response.success) {
            const data = response.data;
            
            // Store room and user info in localStorage
            localStorage.setItem('roomCode', data.roomCode);
            localStorage.setItem('roomName', data.roomName);
            localStorage.setItem('userId', data.userId);
            localStorage.setItem('userName', data.userName);
            localStorage.setItem('isCreator', 'false');
            // CRITICAL: Store JWT token for WebSocket authentication
            localStorage.setItem('sessionToken', data.token);
            localStorage.setItem('token', data.token);  // Backup key
            
            // Show success
            showSuccess(`Joined room: ${data.roomName}`);
            
            // Redirect to chat after 1.5 seconds
            setTimeout(() => {
                window.location.href = 'chat.html';
            }, 1500);
        } else {
            throw new Error(response.error || 'Failed to join room');
        }
    } catch (error) {
        showError(error.message || 'Failed to join room');
        joinBtn.disabled = false;
        joinBtn.textContent = 'Join Room';
    }
});

// Auto-uppercase room code input
document.getElementById('roomCode').addEventListener('input', (e) => {
    e.target.value = e.target.value.toUpperCase();
});
