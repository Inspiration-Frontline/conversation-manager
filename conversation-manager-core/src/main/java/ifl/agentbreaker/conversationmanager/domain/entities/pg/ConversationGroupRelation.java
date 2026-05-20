package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationGroupRelation extends EntityBase
{
    /**
     * ID of the conversation, in string.
     */
    private String conversationId;

    /**
     * ID of the conversation group that the current conversation belongs to.
     * {@code null} if the conversation is not in a group.
     */
    private String conversationGroupId;

    /**
     * Sort order of the conversation in the conversation group.
     */
    private int sortOrder;
}
