package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import lombok.Data;

import java.util.List;

@Data
public class RemoveConversationFromGroupRequest
{
    private long conversationGroupId;
    private List<String> conversationIds;
}
