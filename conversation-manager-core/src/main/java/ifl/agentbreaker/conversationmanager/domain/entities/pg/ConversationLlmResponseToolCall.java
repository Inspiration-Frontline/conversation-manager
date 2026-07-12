package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationLlmResponseToolCall extends ExecutionEntityBase
{
    private long turnId;
    private long llmCallId;
    private int callOrder;
    private String toolCallId;
    private String type;
    private String functionName;
    private String arguments;
}
