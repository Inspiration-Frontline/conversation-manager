package ifl.agentbreaker.conversationmanager.domain.dtos;

import lombok.Data;

@Data
public class ConversationPinOrder
{
    private String conversationId;
    private int sortOrder;
}
