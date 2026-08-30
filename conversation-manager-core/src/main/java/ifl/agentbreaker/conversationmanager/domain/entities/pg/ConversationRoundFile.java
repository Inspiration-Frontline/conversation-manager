package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** Immutable link from one Round to a frozen, owner-authorized file resource. */
@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationRoundFile extends EntityBase
{
    /** Database identifier of the containing Round. */
    private long roundId;
    /** Stable identifier of the file resource. */
    private long fileResourceId;
    /** Numeric file order used for ordering or bounds. */
    private int fileOrder;
}
