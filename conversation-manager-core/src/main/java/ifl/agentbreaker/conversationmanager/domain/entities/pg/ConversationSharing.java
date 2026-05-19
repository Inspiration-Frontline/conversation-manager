package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationSharing extends EntityBase
{
    /**
     * ID of the parent conversation, in string.
     */
    private String parentConversationId;

    /**
     * ID of the current conversation, in string, used in the URL.
     */
    private String sharedConversationId;

    /**
     * ID of the last message in the shared conversation.
     * A user can share a conversation multiple times, with different messages.
     * In this case, each time when a user share a conversation, we should save the last message id,
     * as the range of the messages.
     */
    private long endMessageId;
}
