package ifl.agentbreaker.conversationmanager.domain.dtos.responses;

import lombok.Data;

/** Navigation summary for one Conversation Group, including its current member count. */
@Data
public class ConversationGroupAbstract
{
    /**
     * ID of the conversation group.
     */
    private long groupId;

    /**
     * Name of the conversation group.
     */
    private String name;

    /**
     * Description of the conversation group.
     */
    private String description;

    /**
     * User-defined order in the Group region.
     */
    private int sortOrder;

    /**
     * Number of active Conversations currently assigned to this Group.
     */
    private long conversationCount;
}
