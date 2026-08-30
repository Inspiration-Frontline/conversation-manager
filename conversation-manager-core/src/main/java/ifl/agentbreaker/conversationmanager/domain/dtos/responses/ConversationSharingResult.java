package ifl.agentbreaker.conversationmanager.domain.dtos.responses;

import lombok.Data;

import java.time.Instant;

/** Result returned after creating an authenticated immutable Conversation share. */
@Data
public class ConversationSharingResult
{
    /**
     * ID of the parent conversation, in string.
     */
    private String parentConversationId;

    /**
     * ID of the current conversation, in string, used in the URL.
     */
    private String sharedConversationId;

    /** Inclusive completed-Round boundary frozen into the share. */
    private long endRoundNumber;

    /** UTC expiry instant, or {@code null} when the share does not expire automatically. */
    private Instant expiresAt;
}
