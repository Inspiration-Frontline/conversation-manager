package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationSharing extends EntityBase
{
    /**
     * ID of the parent conversation, in string.
     */
    private String parentConversationId;

    /**
     * ID of the current conversation, in string, used in the URL.
     */
    private String sharedConversationId;

    /** Inclusive upper boundary of the completed Round snapshot. */
    private long endRoundNumber;

    /**
     * Whether the shared conversation remains accessible after the original conversation is deleted.
     */
    private boolean accessibleAfterDeleted;

    private Instant expiresAt;

    private boolean revoked;

    private Instant revokedAt;
}
