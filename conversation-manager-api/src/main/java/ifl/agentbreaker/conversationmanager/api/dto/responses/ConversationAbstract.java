package ifl.agentbreaker.conversationmanager.api.dto.responses;

import lombok.Data;

import java.time.Instant;

/** Owner-scoped Conversation summary used by creation and navigation APIs. */
@Data
public class ConversationAbstract
{
    /** Stable public identifier of the Conversation. */
    private String conversationId;
    /** Current user-visible Conversation title. */
    private String title;
    /** Whether the ungrouped Conversation is pinned in root navigation. */
    private boolean pinned;
    /** Stable identifier of the conversation group. */
    private Long conversationGroupId;
    /** Display name of the current Group, or {@code null} for an ungrouped Conversation. */
    private String conversationGroupName;
    /** UTC instant marking last round updated time. */
    private Instant lastRoundUpdatedTime;
    /** UTC instant when the record was created. */
    private Instant creationTime;
    /** UTC instant when the record was last modified. */
    private Instant modificationTime;
}
