package com.code.HushChat.storage;

import com.code.HushChat.model.UserSession;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemorySessionStore {
    
    private final Map<String, UserSession> sessionStore = new ConcurrentHashMap<>();
    
    public void save(String sessionToken, UserSession session) {
        sessionStore.put(sessionToken, session);
    }
    
    public UserSession get(String sessionToken) {
        return sessionStore.get(sessionToken);
    }
    
    public void delete(String sessionToken) {
        sessionStore.remove(sessionToken);
    }
    
    public Map<String, UserSession> getAll() {
        return new ConcurrentHashMap<>(sessionStore);
    }
    
    public void clear() {
        sessionStore.clear();
    }
    
    public int size() {
        return sessionStore.size();
    }
}