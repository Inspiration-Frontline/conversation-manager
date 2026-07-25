package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class CreateConversationRequest
{
    @Positive(message = "Conversation Group ID must be positive.")
    private Long conversationGroupId;
}
