package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import ifl.agentbreaker.conversationmanager.domain.dtos.responses.ConversationGroupAbstract;
import lombok.Data;

import java.util.List;

@Data
public class ReorderConversationGroupRequest
{
    private List<ConversationGroupAbstract> conversationGroupAbstracts;
}
