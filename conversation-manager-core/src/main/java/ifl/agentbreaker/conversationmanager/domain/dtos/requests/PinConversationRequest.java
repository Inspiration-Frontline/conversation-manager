package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import lombok.Data;

import java.util.List;

@Data
public class PinConversationRequest
{
    private List<String> conversationIds;
    private Boolean pinned;
}
