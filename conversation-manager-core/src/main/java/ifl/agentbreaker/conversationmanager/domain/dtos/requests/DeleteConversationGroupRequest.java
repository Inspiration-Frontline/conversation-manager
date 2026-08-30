package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import jakarta.validation.constraints.Positive;
import lombok.Data;

/** Request to remove an owned conversation group and optionally its conversations. */
@Data
public class DeleteConversationGroupRequest
{
    /** Stable identifier of the group. */
    @Positive(message = "Conversation group ID must be positive.")
    private long groupId;

    /** Whether conversations currently in the group should be deleted as well. */
    private boolean deleteConversations;
}
