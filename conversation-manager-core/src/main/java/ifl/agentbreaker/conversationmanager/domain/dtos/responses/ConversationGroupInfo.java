package ifl.agentbreaker.conversationmanager.domain.dtos.responses;

import ifl.agentbreaker.conversationmanager.api.dto.responses.ConversationAbstract;
import lombok.Data;

import java.util.List;

/** Detailed Group projection containing its ordered Conversation summaries. */
@Data
public class ConversationGroupInfo
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
     * Sort order of the conversation group.
     */
    private int sortOrder;

    /**
     * Conversations in the conversation group.
     */
    private List<ConversationAbstract> conversations;
}
