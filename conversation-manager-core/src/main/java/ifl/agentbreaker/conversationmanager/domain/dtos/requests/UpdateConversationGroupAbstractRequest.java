package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import lombok.Data;

@Data
public class UpdateConversationGroupAbstractRequest
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
}
