package com.code.HushChat.model;

/**
 * Enum representing the type of conversation.
 * 
 * <p>Conversation types:
 * <ul>
 *   <li>ONE_TO_ONE - Direct conversation between two users</li>
 *   <li>GROUP - Group conversation with multiple participants</li>
 * </ul>
 * </p>
 * 
 * @author Hush Chat Development Team
 * @since 2.0
 */
public enum ConversationType {
    /**
     * Direct one-to-one conversation between two users
     */
    ONE_TO_ONE,
    
    /**
     * Group conversation with multiple participants
     */
    GROUP
}
