package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import lombok.Data;

@Data
public class GetConversationHistoryRequest
{
    /**
     * ID of the conversation, in string.
     */
    private String conversationId;

    /**
     * Keyword to search for.
     */
    private String keyword;

    /**
     * Page index.
     */
    private int pageIndex;

    /**
     * Page size.
     */
    private int pageSize;
}
