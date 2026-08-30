package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class ResolveConversationReferencesRequest
{
    /** Stable identifier of the destination conversation. */
    private String destinationConversationId;

    /** Positive Group scope, or zero when an existing destination Conversation supplies the scope. */
    private long conversationGroupId;

    /** Stable identifiers of the selected source conversation values. */
    @NotEmpty(message = "At least one source Conversation is required.")
    private List<String> sourceConversationIds;
}
