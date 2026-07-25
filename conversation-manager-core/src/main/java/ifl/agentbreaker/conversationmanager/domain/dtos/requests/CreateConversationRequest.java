package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateConversationRequest
{
    @Size(max = 64, message = "Conversation Group ID must not exceed 64 characters.")
    private String conversationGroupId;
}
