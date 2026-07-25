package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class DeleteConversationGroupRequest
{
    @NotNull(message = "Conversation group ID is required.")
    @Positive(message = "Conversation group ID must be positive.")
    private Long groupId;

    private boolean deleteConversations;
}
