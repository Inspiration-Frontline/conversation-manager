package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

/** Durable root aggregate for one user's Conversation and its Round-number high-water mark. */
@Data
@EqualsAndHashCode(callSuper = true)
public class Conversation extends EntityBase
{
    /**
     * ID of the conversation, in string.
     */
    private String conversationId;

    /**
     * Title of the conversation.
     */
    private String title;

    /**
     * Whether the conversation is pinned in the root conversation list.
     */
    private boolean pinned;

    /**
     * Optional Group membership. A null value means the Conversation is displayed at root level.
     */
    private Long conversationGroupId;

    /**
     * Group display name populated by navigation queries.
     */
    private String conversationGroupName;

    /**
     * Time of the newest durably persisted Round, independent from metadata updates.
     */
    private Instant lastRoundUpdatedTime;

    /**
     * Highest round number ever assigned to this conversation.
     * Logical deletion never decreases this value.
     */
    private long latestRoundNumber;

    /** Whether the Conversation is hidden while historical sharing and audit references to remain intact. */
    private boolean deleted;
}
