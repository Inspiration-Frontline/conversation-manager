package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

@Data
public class AddConversationToGroupRequest
{
    @NotNull(message = "Conversation group ID is required.")
    @Positive(message = "Conversation group ID must be positive.")
    private Long conversationGroupId;

    @NotEmpty(message = "Conversation IDs are required.")
    private List<String> conversationIds;
}
