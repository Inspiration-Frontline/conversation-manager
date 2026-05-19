package ifl.agentbreaker.conversationmanager.domain.dtos.responses;

import lombok.Data;

@Data
public class ConversationGroupAbstract
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
