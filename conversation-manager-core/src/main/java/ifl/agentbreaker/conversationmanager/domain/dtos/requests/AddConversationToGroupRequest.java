package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

/** Request that moves selected owned Conversations into one owned Group. */
@Data
public class AddConversationToGroupRequest
{
    /** Stable identifier of the conversation group. */
    @Positive(message = "Conversation group ID must be positive.")
    private long conversationGroupId;

    /** Stable identifiers of the selected conversation values. */
    @NotEmpty(message = "Conversation IDs are required.")
    private List<String> conversationIds;
}
