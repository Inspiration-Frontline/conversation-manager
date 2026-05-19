package ifl.agentbreaker.conversationmanager.api.dto.responses;

import ifl.agentbreaker.conversationmanager.domain.dtos.responses.ConversationMessageInfo;
import lombok.Data;

import java.util.List;

@Data
public class ConversationMessageHistory
{
    private String conversationId;
    private String title;
    private List<ConversationMessageInfo> messages;
}
