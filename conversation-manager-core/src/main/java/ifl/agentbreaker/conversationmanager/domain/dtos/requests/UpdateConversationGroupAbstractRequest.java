package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class UpdateConversationGroupAbstractRequest
{
    /**
     * ID of the conversation group.
     */
    @Positive(message = "Conversation group ID must be positive.")
    private long groupId;

    /**
     * Name of the conversation group.
     */
    @NotBlank(message = "Name of the group is required.")
    private String name;

    /**
     * Description of the conversation group.
     */
    private String description;
}
