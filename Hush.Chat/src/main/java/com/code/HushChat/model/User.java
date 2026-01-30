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
 * User entity representing a user in the chat system.
 * Supports both anonymous and registered users.
 * 
 * <p>Anonymous users have null email and password.</p>
 * <p>Registered users must have email and hashed password.</p>
 * 
 * @author Hush Chat Development Team
 * @since 2.0
 */
@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    /**
     * Primary key - UUID auto-generated
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    /**
     * Email address - nullable for anonymous users, unique when not null
     */
    @Column(name = "email", unique = true, length = 255)
    private String email;

    /**
     * BCrypt hashed password - nullable for anonymous users
     */
    @Column(name = "password", length = 255)
    private String password;

    /**
     * Flag indicating if this is an anonymous user
     * Default: true
     */
    @Column(name = "is_anonymous", nullable = false)
    @Builder.Default
    private boolean isAnonymous = true;

    /**
     * Timestamp when the user was created
     * Automatically set on entity creation
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp when the user was last updated
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
        User user = (User) o;
        return id != null && Objects.equals(id, user.id);
    }

    /**
     * HashCode based on id for proper entity hashing
     */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /**
     * ToString method - excludes password for security
     */
    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", email='" + email + '\'' +
                ", isAnonymous=" + isAnonymous +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
