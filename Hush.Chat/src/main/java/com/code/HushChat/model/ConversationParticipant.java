package com.code.HushChat.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * ConversationParticipant entity representing the many-to-many relationship
 * between users and conversations.
 * 
 * <p>This is a join table that tracks which users are part of which conversations,
 * along with metadata like join time and last read timestamp for unread counts.</p>
 * 
 * <p>Constraints:
 * <ul>
 *   <li>Composite unique constraint on (conversationId, userId)</li>
 *   <li>A user cannot be added to the same conversation twice</li>
 *   <li>Indexes on conversationId and userId for fast lookups</li>
 * </ul>
 * </p>
 * 
 * @author Hush Chat Development Team
 * @since 2.0
 */
@Entity
@Table(name = "conversation_participants",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_conversation_user", columnNames = {"conversation_id", "user_id"})
    },
    indexes = {
        @Index(name = "idx_conversation_id", columnList = "conversation_id"),
        @Index(name = "idx_user_id", columnList = "user_id")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationParticipant {

    /**
     * Primary key - UUID auto-generated
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    /**
     * Foreign key reference to the Conversation
     * Uses LAZY fetch to avoid unnecessary joins
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false, foreignKey = @ForeignKey(name = "fk_participant_conversation"))
    private Conversation conversation;

    /**
     * Foreign key reference to the User
     * Uses LAZY fetch to avoid unnecessary joins
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_participant_user"))
    private User user;

    /**
     * Timestamp when the user joined this conversation
     * Automatically set on entity creation
     */
    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    /**
     * Timestamp of the last message read by this user
     * Nullable - null means user hasn't read any messages yet
     * Used to calculate unread message count
     */
    @Column(name = "last_read_at")
    private LocalDateTime lastReadAt;

    /**
     * Equals method based on id for proper entity comparison
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConversationParticipant that = (ConversationParticipant) o;
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
     * Excludes lazy-loaded relationships to avoid LazyInitializationException
     */
    @Override
    public String toString() {
        return "ConversationParticipant{" +
                "id=" + id +
                ", conversationId=" + (conversation != null ? conversation.getId() : null) +
                ", userId=" + (user != null ? user.getId() : null) +
                ", joinedAt=" + joinedAt +
                ", lastReadAt=" + lastReadAt +
                '}';
    }
}
