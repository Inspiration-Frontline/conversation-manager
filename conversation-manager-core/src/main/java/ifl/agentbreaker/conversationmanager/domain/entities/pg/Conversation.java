package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

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
    private String conversationGroupId;

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

    /**
     * Whether the conversation is deleted.
     */
    // Here we use a logic deletion instead of a hard deletion because the shared conversation may contain the deleted conversation, since the shared conversation is a snapshot of the parent conversation.
    private boolean deleted;
}
