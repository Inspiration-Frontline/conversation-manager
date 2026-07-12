package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationLlmRequestMessageToolCall extends ExecutionEntityBase
{
    private long requestMessageId;
    private int callOrder;
    private String toolCallId;
    private String type;
    private String functionName;
    private String arguments;
}
