package com.code.HushChat.storage;

import com.code.HushChat.model.FileMetadata;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryFileStore {
    
    // fileId -> FileMetadata
    private final Map<String, FileMetadata> fileStore = new ConcurrentHashMap<>();
    // roomCode -> Set of fileIds
    private final Map<String, Set<String>> roomFilesIndex = new ConcurrentHashMap<>();
    
    public void save(String fileId, FileMetadata metadata) {
        fileStore.put(fileId, metadata);
        
        // Index by room
        roomFilesIndex.computeIfAbsent(metadata.getRoomCode(), 
            k -> ConcurrentHashMap.newKeySet())
            .add(fileId);
    }
    
    public FileMetadata get(String fileId) {
        return fileStore.get(fileId);
    }
    
    public void delete(String fileId) {
        FileMetadata metadata = fileStore.remove(fileId);
        if (metadata != null) {
            Set<String> roomFiles = roomFilesIndex.get(metadata.getRoomCode());
            if (roomFiles != null) {
                roomFiles.remove(fileId);
            }
        }
    }
    
    public Set<String> getRoomFiles(String roomCode) {
        return roomFilesIndex.getOrDefault(roomCode, Collections.emptySet());
    }
    
    public void deleteRoomFiles(String roomCode) {
        Set<String> fileIds = roomFilesIndex.remove(roomCode);
        if (fileIds != null) {
            fileIds.forEach(fileStore::remove);
        }
    }
    
    public Map<String, FileMetadata> getAll() {
        return new ConcurrentHashMap<>(fileStore);
    }
    
    public void clear() {
        fileStore.clear();
        roomFilesIndex.clear();
    }
    
    public int size() {
        return fileStore.size();
    }
}