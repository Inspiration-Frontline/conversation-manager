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
     * Page index.
     */
    private int pageIndex;

    /**
     * Page size.
     */
    private int pageSize;
}
