package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Request for creating one owner-scoped Conversation Group. */
@Data
public class CreateConversationGroupRequest
{
    /** Required Group display name before normalization and length limiting. */
    @NotNull(message = "Name is required.")
    private String name;

    /** Optional organization-only description that never enters model context. */
    private String description;
}
