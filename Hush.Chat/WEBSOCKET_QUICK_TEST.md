# 🚀 Quick Test Guide - WebSocket Message Delivery Fix

## ⚡ Quick Verification (5 Minutes)

### Prerequisites
```bash
# 1. Ensure WebSocket mode is enabled
grep "realtime.mode=WEBSOCKET" src/main/resources/application.properties

# 2. Start the application
./run-script.bat
# OR
mvn spring-boot:run
```

### Test 1: Two-User Message Exchange (2 minutes)

1. **Open two browser windows side-by-side**
   - Window A: http://localhost:8080
   - Window B: http://localhost:8080 (incognito or different browser)

2. **Window A: Create Room**
   - Click "Create New Room"
   - Copy the 5-character room code (e.g., ABC123)
   - Choose username: "Alice"

3. **Window B: Join Room**
   - Enter the room code from step 2
   - Choose username: "Bob"

4. **Test Message Delivery**
   ```
   Alice → Types "Hello Bob" → Press Enter
   ✅ Bob sees "Hello Bob" appear INSTANTLY (no refresh)
   
   Bob → Types "Hi Alice!" → Press Enter
   ✅ Alice sees "Hi Alice!" appear INSTANTLY
   ```

5. **Open Browser Console (F12)**
   ```javascript
   // Alice's console should show:
   [WebSocket] Connected successfully
   [WebSocket] Subscribed to: /user/queue/room/ABC123
   
   // When Bob sends message:
   [WebSocket] Received event: MESSAGE
   [EventProcessor] Processed 1/1 events (0 duplicates)
   ```

### Test 2: Emoji Reactions (1 minute)

1. **Alice sends message:** "React to this!"
2. **Bob hovers over message** → Click ❤️ emoji
3. **✅ Alice sees:** Heart emoji appear under message instantly
4. **Alice clicks** 😂 emoji
5. **✅ Bob sees:** Both ❤️ and 😂 emojis with counts

### Test 3: Message Actions (1 minute)

1. **Alice sends:** "Original message"
2. **Alice right-clicks** → Edit → Change to "Edited message"
3. **✅ Bob sees:** Message updated with "(edited)" label
4. **Alice right-clicks** → Unsend
5. **✅ Bob sees:** "Message deleted"

### Test 4: File Sharing (1 minute)

1. **Alice clicks** 📎 attachment icon
2. **Select image** (JPG/PNG)
3. **✅ Bob sees:** File message appear with thumbnail
4. **Bob clicks** thumbnail → Image preview modal opens

---

## 🛠️ Debugging Failed Tests

### Problem: Bob doesn't receive Alice's messages

#### Check 1: Verify WebSocket Connection
```javascript
// In Bob's browser console (F12):
webSocketAdapter.getStatus()

// Expected output:
{
  connected: true,
  roomCode: "ABC123",
  hasSubscription: true,
  reconnectAttempts: 0
}

// If connected: false
→ Check server logs for authentication errors
→ Verify JWT token in localStorage (key: 'token')
```

#### Check 2: Backend Logs (Server Console)
```
# Look for these logs when Alice sends message:

✅ WebSocket event dispatched: eventType=MESSAGE, roomCode=ABC123, recipients=1/1
✅ WebSocket message sent to userId=bob_user_id at /user/bob_user_id/queue/room/ABC123

# If you see:
❌ "No WebSocket users in room ABC123"
→ Bob's WebSocket not connected or not subscribed

# If you see:
❌ "Session ABC123 not found in registry"
→ CustomHandshakeHandler not registered correctly
```

#### Check 3: Verify Principal is Set
```bash
# In server logs, search for:
grep "Created StompPrincipal" logs/spring-boot-application.log

# Should show entries like:
Created StompPrincipal for WebSocket session: userId=alice_user_id
Created StompPrincipal for WebSocket session: userId=bob_user_id

# If NOT found:
→ CustomHandshakeHandler not being invoked
→ Check WebSocketConfig.java has .setHandshakeHandler(handshakeHandler)
```

#### Check 4: Network Tab (Browser DevTools)
```
1. Open DevTools → Network tab
2. Filter: WS (WebSockets)
3. Click the WebSocket connection
4. Look at "Messages" tab

✅ Should see:
- CONNECTED frame
- SUBSCRIBE /user/queue/room/ABC123
- MESSAGE frames from server

❌ If no CONNECTED:
→ Authentication failed
→ Check query parameter ?token=xxx in connection URL

❌ If no SUBSCRIBE:
→ Frontend subscription logic failed
→ Check browser console for errors
```

---

## 🔧 Common Issues & Solutions

### Issue 1: "realtime.mode not found"
```bash
# Solution: Add to application.properties
echo "realtime.mode=WEBSOCKET" >> src/main/resources/application.properties

# Then restart application
```

### Issue 2: "CustomHandshakeHandler not autowired"
```bash
# Solution: Ensure @Component annotation exists
# File: CustomHandshakeHandler.java
@Component  // ← This must be present
@Slf4j
public class CustomHandshakeHandler extends DefaultHandshakeHandler {
```

### Issue 3: Compilation Errors
```bash
# Rebuild project
mvn clean compile

# If errors persist, check:
- Java version: 17 or 21
- Spring Boot version: 3.3.x
- Dependencies in pom.xml include spring-boot-starter-websocket
```

### Issue 4: Frontend Shows Polling Instead of WebSocket
```javascript
// Check transport factory status
transportFactory.activeTransport

// Expected: "websocket"
// If: "polling"
→ WebSocket connection failed
→ Check backend logs for handshake errors
→ Verify JWT token exists: localStorage.getItem('token')
```

---

## 📊 Success Criteria Checklist

### ✅ WebSocket Connection
- [ ] Backend logs show: "Created StompPrincipal for WebSocket session"
- [ ] Backend logs show: "User registered in room via WebSocket"
- [ ] Frontend console shows: "[WebSocket] Connected successfully"
- [ ] Frontend console shows: "[WebSocket] Subscribed to: /user/queue/room/..."

### ✅ Message Delivery
- [ ] User A sends message → User B receives in <100ms (no page reload)
- [ ] User B sends message → User A receives in <100ms
- [ ] Backend logs show: "WebSocket message sent to userId=xxx"
- [ ] Frontend console shows: "[WebSocket] Received event: MESSAGE"

### ✅ Real-Time Features
- [ ] Emoji reactions appear instantly on both sides
- [ ] Message edits show "(edited)" label immediately
- [ ] Message deletes show "Message deleted" immediately
- [ ] File uploads appear as messages immediately

### ✅ Fallback & Compatibility
- [ ] Setting `realtime.mode=LONG_POLL` still works
- [ ] Closing WebSocket → Polling takes over automatically
- [ ] Multiple tabs per user → All tabs receive messages

---

## 🎯 Expected Performance

### WebSocket Mode (realtime.mode=WEBSOCKET)
```
Message Latency: <100ms (instant delivery)
Server Load:     ~50% reduction vs polling
Network Traffic: ~70% reduction vs polling
Battery Impact:  Minimal (no periodic HTTP requests)
```

### Long Polling Mode (realtime.mode=LONG_POLL)
```
Message Latency: 0-2000ms (depends on poll interval)
Server Load:     Baseline
Network Traffic: 1 HTTP request per 2 seconds per user
Battery Impact:  Moderate (periodic requests)
```

---

## 🚀 Load Testing (Optional)

### Simulate 10 Users in Same Room
```bash
# Install dependencies
npm install -g artillery

# Create load test config
cat > websocket-load-test.yml << EOF
config:
  target: "http://localhost:8080"
  phases:
    - duration: 60
      arrivalRate: 1
      name: "10 concurrent users"
scenarios:
  - engine: ws
    flow:
      - connect:
          url: "/ws?token={{token}}"
      - think: 2
      - send:
          type: "MESSAGE"
          payload: "Hello from load test"
      - think: 30
EOF

# Run test
artillery run websocket-load-test.yml

# Expected results:
✅ All messages delivered
✅ No error messages in logs
✅ Response time < 200ms
```

---

## 📞 Support Contacts

### Logs Location
```bash
# Application logs
tail -f logs/spring-boot-application.log

# WebSocket specific logs
grep "WebSocket" logs/spring-boot-application.log

# Error logs only
grep "ERROR" logs/spring-boot-application.log
```

### Key Files for Troubleshooting
1. `StompPrincipal.java` - Principal implementation
2. `CustomHandshakeHandler.java` - Principal assignment
3. `WebSocketConfig.java` - Endpoint registration
4. `WebSocketDispatcher.java` - Message routing
5. `webSocketAdapter.js` - Client connection

### Configuration Files
1. `application.properties` - Transport mode config
2. `WebSocketConfig.java` - STOMP broker settings
3. `config.js` (frontend) - WebSocket enable/disable

---

## ✅ Done Testing?

If all tests pass:
1. ✅ Mark WEBSOCKET_FIX_REPORT.md as reviewed
2. ✅ Document any environment-specific configurations
3. ✅ Update README.md with WebSocket instructions
4. ✅ Deploy to staging environment
5. ✅ Monitor server logs for 24 hours
6. ✅ Promote to production

---

**Happy Testing! 🎉**
