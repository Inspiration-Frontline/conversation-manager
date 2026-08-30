package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import lombok.Data;

import java.util.List;

@Data
public class PinConversationRequest
{
    /** Stable identifiers of the selected conversation values. */
    private List<String> conversationIds;
    /** Desired pinned state; only root-level Conversations may be pinned. */
    private Boolean pinned;
}
