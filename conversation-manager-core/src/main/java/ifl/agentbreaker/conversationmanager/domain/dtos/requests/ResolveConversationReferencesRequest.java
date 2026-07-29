package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

@Data
public class ResolveConversationReferencesRequest
{
    private String destinationConversationId;

    @Positive(message = "Conversation Group ID must be positive.")
    private Long conversationGroupId;

    @NotEmpty(message = "At least one source Conversation is required.")
    private List<String> sourceConversationIds;
}
