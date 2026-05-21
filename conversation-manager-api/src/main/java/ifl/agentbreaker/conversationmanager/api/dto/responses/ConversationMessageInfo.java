package ifl.agentbreaker.conversationmanager.api.dto.responses;

import ifl.agentbreaker.conversationmanager.api.constants.MessageRole;
import ifl.agentbreaker.conversationmanager.api.dto.ContentPart;
import ifl.agentbreaker.conversationmanager.api.dto.ToolCall;
import lombok.Data;

import java.util.List;

@Data
public class ConversationMessageInfo
{
    private long id;

    private MessageRole role;

    private String name;

    private String content;

    private List<ContentPart> contentParts;

    private List<ToolCall> toolCalls;

    private String toolCallId;

    private Long agentId;

    private String finishReason;
}
