package com.code.HushChat.repository;

import com.code.HushChat.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for User entity.
 * Provides CRUD operations and custom query methods for User management.
 * 
 * @author Hush Chat Development Team
 * @since 2.0
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Find a user by email address.
     * 
     * @param email the email address to search for
     * @return Optional containing the user if found, empty otherwise
     */
    Optional<User> findByEmail(String email);

    /**
     * Check if a user exists with the given email.
     * 
     * @param email the email address to check
     * @return true if a user with this email exists, false otherwise
     */
    boolean existsByEmail(String email);

    /**
     * Find all anonymous users.
     * Useful for cleanup operations or analytics.
     * 
     * @param isAnonymous true to find anonymous users, false for registered users
     * @return list of users matching the criteria
     */
    java.util.List<User> findByIsAnonymous(boolean isAnonymous);
}
