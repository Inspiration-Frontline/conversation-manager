package ifl.agentbreaker.conversationmanager.api.dto.responses;

import lombok.Data;

import java.util.List;

@Data
public class ConversationMessageHistory
{
    private String conversationId;
    private String title;
    private List<ConversationMessageInfo> messages;
}
