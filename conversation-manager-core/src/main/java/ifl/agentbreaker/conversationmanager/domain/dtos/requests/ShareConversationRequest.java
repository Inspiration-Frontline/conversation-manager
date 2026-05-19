package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import lombok.Data;

@Data
public class ShareConversationRequest
{
    private String conversationId;
    private boolean accessibleAfterDeleted;
}
