package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import jakarta.validation.constraints.Positive;
import lombok.Data;

/** Request for creating a Conversation root, optionally inside one owned Group. */
@Data
public class CreateConversationRequest
{
    /** Stable identifier of the conversation group. */
    @Positive(message = "Conversation Group ID must be positive.")
    private Long conversationGroupId;
}
