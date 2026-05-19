package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import ifl.agentbreaker.conversationmanager.domain.dtos.ConversationPinOrder;
import lombok.Data;

import java.util.List;

@Data
public class PinConversationRequest
{
    private List<ConversationPinOrder> conversationPinOrders;
}
