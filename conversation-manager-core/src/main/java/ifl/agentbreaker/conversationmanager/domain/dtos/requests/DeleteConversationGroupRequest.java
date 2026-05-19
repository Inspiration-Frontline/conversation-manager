package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import lombok.Data;

@Data
public class DeleteConversationGroupRequest
{
    private long id;
    private boolean deleteConversations;
}
