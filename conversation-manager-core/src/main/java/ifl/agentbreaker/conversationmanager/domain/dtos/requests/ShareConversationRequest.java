package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ShareConversationRequest
{
    @NotBlank(message = "Conversation ID is required.")
    private String conversationId;

    private boolean accessibleAfterDeleted;
}
