package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class DeleteConversationGroupRequest
{
    @Positive(message = "Conversation group ID must be positive.")
    private long groupId;

    private boolean deleteConversations;
}
