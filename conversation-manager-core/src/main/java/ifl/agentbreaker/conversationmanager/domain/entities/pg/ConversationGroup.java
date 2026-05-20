package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationGroup extends EntityBase
{
    /**
     * ID of the conversation group, in string.
     */
    private String groupId;

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
