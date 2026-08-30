package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ForkConversationRequest
{
    /** Stable identifier of the shared conversation. */
    @NotBlank(message = "Shared conversation ID is required.")
    private String sharedConversationId;
}
