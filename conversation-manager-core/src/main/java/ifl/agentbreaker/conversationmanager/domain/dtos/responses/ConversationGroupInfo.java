package ifl.agentbreaker.conversationmanager.domain.dtos.responses;

import lombok.Data;

import java.util.List;

@Data
public class ConversationGroupInfo
{
    /**
     * ID of the conversation group.
     */
    private long id;

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

    /**
     * Conversations in the conversation group.
     */
    private List<ifl.agentbreaker.conversationmanager.api.dto.responses.ConversationAbstract> conversations;
}
