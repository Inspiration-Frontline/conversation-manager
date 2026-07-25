package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class MoveConversationsRequest
{
    @NotEmpty(message = "Conversation IDs are required.")
    private List<String> conversationIds;

    /**
     * Target Group ID, or null to move the Conversations back to the root list.
     */
    private Long targetConversationGroupId;
}
