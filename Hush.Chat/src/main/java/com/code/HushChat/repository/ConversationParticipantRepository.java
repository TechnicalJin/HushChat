package com.code.HushChat.repository;

import com.code.HushChat.model.Conversation;
import com.code.HushChat.model.ConversationParticipant;
import com.code.HushChat.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for ConversationParticipant entity.
 * Manages the many-to-many relationship between users and conversations.
 * 
 * @author Hush Chat Development Team
 * @since 2.0
 */
@Repository
public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, UUID> {

    /**
     * Find all participants in a specific conversation.
     * 
     * @param conversationId the UUID of the conversation
     * @return list of participants in the conversation
     */
    @Query("SELECT cp FROM ConversationParticipant cp WHERE cp.conversation.id = :conversationId")
    List<ConversationParticipant> findByConversationId(@Param("conversationId") UUID conversationId);

    /**
     * Find all conversations a user is participating in.
     * 
     * @param userId the UUID of the user
     * @return list of conversation participants for this user
     */
    @Query("SELECT cp FROM ConversationParticipant cp WHERE cp.user.id = :userId")
    List<ConversationParticipant> findByUserId(@Param("userId") UUID userId);

    /**
     * Check if a user is a participant in a specific conversation.
     * 
     * @param conversationId the UUID of the conversation
     * @param userId the UUID of the user
     * @return true if the user is a participant, false otherwise
     */
    @Query("SELECT COUNT(cp) > 0 FROM ConversationParticipant cp WHERE cp.conversation.id = :conversationId AND cp.user.id = :userId")
    boolean existsByConversationIdAndUserId(@Param("conversationId") UUID conversationId, @Param("userId") UUID userId);

    /**
     * Find a specific participant record by conversation and user.
     * 
     * @param conversationId the UUID of the conversation
     * @param userId the UUID of the user
     * @return Optional containing the participant if found
     */
    @Query("SELECT cp FROM ConversationParticipant cp WHERE cp.conversation.id = :conversationId AND cp.user.id = :userId")
    Optional<ConversationParticipant> findByConversationIdAndUserId(@Param("conversationId") UUID conversationId, @Param("userId") UUID userId);

    /**
     * Find participants by conversation entity (alternative to ID-based query).
     * 
     * @param conversation the conversation entity
     * @return list of participants in the conversation
     */
    List<ConversationParticipant> findByConversation(Conversation conversation);

    /**
     * Find participants by user entity (alternative to ID-based query).
     * 
     * @param user the user entity
     * @return list of conversation participants for this user
     */
    List<ConversationParticipant> findByUser(User user);

    /**
     * Count participants in a conversation.
     * 
     * @param conversationId the UUID of the conversation
     * @return number of participants in the conversation
     */
    @Query("SELECT COUNT(cp) FROM ConversationParticipant cp WHERE cp.conversation.id = :conversationId")
    long countByConversationId(@Param("conversationId") UUID conversationId);

    /**
     * Delete all participants in a conversation.
     * Useful when deleting a conversation.
     * 
     * @param conversationId the UUID of the conversation
     */
    @Query("DELETE FROM ConversationParticipant cp WHERE cp.conversation.id = :conversationId")
    void deleteByConversationId(@Param("conversationId") UUID conversationId);
}
