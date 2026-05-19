package ifl.agentbreaker.conversationmanager.api.dto.requests;

import lombok.Data;

import java.util.List;

@Data
public class DeleteMessagesRequest
{
    private String conversationId;
    private List<Long> messageIds;
}
