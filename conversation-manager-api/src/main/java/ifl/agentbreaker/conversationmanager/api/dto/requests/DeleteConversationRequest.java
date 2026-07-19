package ifl.agentbreaker.conversationmanager.api.dto.requests;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeleteConversationRequest
{
    @NotBlank(message = "Conversation ID is required.")
    private String conversationId;
}
