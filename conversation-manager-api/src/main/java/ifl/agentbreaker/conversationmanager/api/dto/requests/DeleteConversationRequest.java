package ifl.agentbreaker.conversationmanager.api.dto.requests;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;

/** Batch deletion request for one or more owned Conversations. */
@Data
public class DeleteConversationRequest
{
    /** Stable identifiers of the selected conversation values. */
    @NotEmpty(message = "At least one conversation is required.")
    private List<String> conversationIds;
}
