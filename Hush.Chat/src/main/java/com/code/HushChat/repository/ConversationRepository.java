package com.code.HushChat.repository;

import com.code.HushChat.model.Conversation;
import com.code.HushChat.model.ConversationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository interface for Conversation entity.
 * Provides CRUD operations and custom query methods for Conversation management.
 * 
 * @author Hush Chat Development Team
 * @since 2.0
 */
@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    /**
     * Find all conversations ordered by last message timestamp in descending order.
     * Most recent conversations appear first.
     * Conversations with null lastMessageAt will appear last.
     * 
     * @return list of conversations sorted by last message time (newest first)
     */
    List<Conversation> findAllByOrderByLastMessageAtDesc();

    /**
     * Find conversations by type.
     * 
     * @param type the conversation type (ONE_TO_ONE or GROUP)
     * @return list of conversations of the specified type
     */
    List<Conversation> findByType(ConversationType type);

    /**
     * Find conversations by type, ordered by last message timestamp.
     * 
     * @param type the conversation type
     * @return list of conversations sorted by last message time (newest first)
     */
    List<Conversation> findByTypeOrderByLastMessageAtDesc(ConversationType type);
}
