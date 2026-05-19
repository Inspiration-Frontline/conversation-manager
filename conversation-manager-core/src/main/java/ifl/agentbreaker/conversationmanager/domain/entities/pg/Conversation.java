package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class Conversation extends EntityBase
{
    /**
     * ID of the conversation, in string.
     */
    private String conversationId;

    /**
     * Title of the conversation.
     */
    private String title;

    /**
     * Whether the conversation is deleted.
     */
    // Here we use a logic deletion instead of a hard deletion because the shared conversation may contain the deleted conversation, since the shared conversation is a snapshot of the parent conversation.
    private boolean deleted;
}
