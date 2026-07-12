package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import ifl.agentbreaker.conversationmanager.domain.constants.LlmMessageRole;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationLlmRequestMessage extends ExecutionEntityBase
{
    private long llmCallId;
    private int messageOrder;
    private LlmMessageRole role;
    private String content;
    private String contentParts;
    private String toolCallId;
}
