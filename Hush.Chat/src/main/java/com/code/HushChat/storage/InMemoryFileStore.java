package com.code.HushChat.storage;

import com.code.HushChat.model.FileMetadata;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory storage for file metadata.
 * Thread-safe implementation using ConcurrentHashMap.
 * 
 * Maintains two indices:
 * - fileStore: fileId -> FileMetadata
 * - roomFilesIndex: roomCode -> Set<fileId>
 */
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
                // Clean up empty room index entry
                if (roomFiles.isEmpty()) {
                    roomFilesIndex.remove(metadata.getRoomCode());
                }
            }
        }
    }
    
    public Set<String> getRoomFiles(String roomCode) {
        Set<String> files = roomFilesIndex.get(roomCode);
        // Return a copy to prevent concurrent modification issues
        return files != null ? new HashSet<>(files) : Collections.emptySet();
    }
    
    public void deleteRoomFiles(String roomCode) {
        Set<String> fileIds = roomFilesIndex.remove(roomCode);
        if (fileIds != null) {
            fileIds.forEach(fileStore::remove);
        }
    }
    
    /**
     * Get a copy of all file metadata for safe iteration during cleanup.
     * @return copy of all file metadata entries
     */
    public Map<String, FileMetadata> getAll() {
        return new ConcurrentHashMap<>(fileStore);
    }
    
    /**
     * Get all expired files for cleanup.
     * Returns file IDs to avoid holding references during iteration.
     * @return list of expired file IDs
     */
    public List<String> getExpiredFileIds() {
        return fileStore.entrySet().stream()
            .filter(entry -> entry.getValue().isExpired())
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }
    
    /**
     * Get all file paths for a room.
     * Useful for disk cleanup when room expires.
     * @param roomCode the room code
     * @return list of file paths (may include nulls if path not set)
     */
    public List<String> getRoomFilePaths(String roomCode) {
        Set<String> fileIds = roomFilesIndex.get(roomCode);
        if (fileIds == null || fileIds.isEmpty()) {
            return Collections.emptyList();
        }
        
        return fileIds.stream()
            .map(fileStore::get)
            .filter(Objects::nonNull)
            .map(FileMetadata::getFilePath)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    
    public void clear() {
        fileStore.clear();
        roomFilesIndex.clear();
    }
    
    public int size() {
        return fileStore.size();
    }
    
    /**
     * Check if a room has any files.
     * @param roomCode the room code
     * @return true if room has files
     */
    public boolean hasFilesForRoom(String roomCode) {
        Set<String> files = roomFilesIndex.get(roomCode);
        return files != null && !files.isEmpty();
    }
}