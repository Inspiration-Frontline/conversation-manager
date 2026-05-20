package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ForkConversationRequest
{
    @NotBlank(message = "Shared conversation ID is required.")
    private String sharedConversationId;
}
