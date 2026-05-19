package ifl.agentbreaker.conversationmanager.domain.dtos.responses;

import lombok.Data;

@Data
public class ConversationSharingResult
{
    /**
     * ID of the parent conversation, in string.
     */
    private String parentConversationId;

    /**
     * ID of the current conversation, in string, used in the URL.
     */
    private String sharedConversationId;
}
