package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** Persisted owner-scoped Group used to order Conversations in navigation. */
@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationGroup extends EntityBase
{
    /**
     * Name of the conversation group.
     */
    private String name;

    /**
     * Description of the conversation group.
     */
    private String description;

    /**
     * Sort order of the conversation group.
     */
    private int sortOrder;

}
