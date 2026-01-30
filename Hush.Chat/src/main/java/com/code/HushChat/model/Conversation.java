package com.code.HushChat.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Conversation entity representing a chat conversation.
 * Supports both one-to-one and group conversations.
 * 
 * <p>This entity stores conversation metadata. Participants and messages
 * are stored in separate tables for better normalization.</p>
 * 
 * @author Hush Chat Development Team
 * @since 2.0
 */
@Entity
@Table(name = "conversations", indexes = {
    @Index(name = "idx_last_message_at", columnList = "last_message_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {

    /**
     * Primary key - UUID auto-generated
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    /**
     * Type of conversation (ONE_TO_ONE or GROUP)
     * Stored as STRING in database for readability
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private ConversationType type;

    /**
     * Timestamp when the conversation was created
     * Automatically set on entity creation
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp of the last message in this conversation
     * Nullable for new conversations with no messages yet
     * Indexed for efficient sorting by recent activity
     */
    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    /**
     * Timestamp when the conversation was last updated
     * Automatically updated on entity modification
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Equals method based on id for proper entity comparison
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Conversation that = (Conversation) o;
        return id != null && Objects.equals(id, that.id);
    }

    /**
     * HashCode based on id for proper entity hashing
     */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /**
     * ToString method for debugging
     */
    @Override
    public String toString() {
        return "Conversation{" +
                "id=" + id +
                ", type=" + type +
                ", createdAt=" + createdAt +
                ", lastMessageAt=" + lastMessageAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
