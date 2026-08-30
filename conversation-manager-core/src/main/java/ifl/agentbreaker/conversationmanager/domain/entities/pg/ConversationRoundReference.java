package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** Frozen evidence link from a destination Round to an owned source Conversation boundary. */
@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationRoundReference extends EntityBase
{
    /** Database identifier of the containing Round. */
    private long roundId;
    /** Stable identifier of the source conversation. */
    private String sourceConversationId;
    /** Inclusive source Round boundary captured when the destination request was sent. */
    private long sourceEndRoundNumber;
    /** Source title snapshot retained for provenance after later renames. */
    private String sourceTitle;
    /** Zero-based position of this reference in the destination request. */
    private int referenceOrder;
}
