package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

/** Persisted authenticated share token and its immutable completed-Round snapshot boundary. */
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

    /** UTC expiry instant, or {@code null} for a share that does not expire automatically. */
    private Instant expiresAt;

    /** Whether the owner has explicitly revoked this share. */
    private boolean revoked;

    /** UTC revocation instant, or {@code null} while the share is active. */
    private Instant revokedAt;
}
