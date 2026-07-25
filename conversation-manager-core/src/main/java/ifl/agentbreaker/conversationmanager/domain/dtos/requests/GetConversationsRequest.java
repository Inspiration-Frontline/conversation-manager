package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import lombok.Data;

@Data
public class GetConversationsRequest
{
    /**
     * Keyword to search for in conversation titles.
     */
    private String keyword;

    /**
     * Optional Group whose Conversations should be returned.
     */
    private String conversationGroupId;

    /**
     * Whether title search should include both grouped and root Conversations.
     */
    private boolean includeGrouped;

    /**
     * Page index.
     */
    private int pageIndex;

    /**
     * Page size.
     */
    private int pageSize;
}
