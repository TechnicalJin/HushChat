package com.code.HushChat.storage;

import com.code.HushChat.model.OtpSession;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryOtpStore {
    
    private final Map<String, OtpSession> otpStore = new ConcurrentHashMap<>();
    
    public void save(String deviceId, OtpSession session) {
        otpStore.put(deviceId, session);
    }
    
    public OtpSession get(String deviceId) {
        return otpStore.get(deviceId);
    }
    
    public void delete(String deviceId) {
        otpStore.remove(deviceId);
    }
    
    public Map<String, OtpSession> getAll() {
        return new ConcurrentHashMap<>(otpStore);
    }
    
    public void clear() {
        otpStore.clear();
    }
    
    public int size() {
        return otpStore.size();
    }
}