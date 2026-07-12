package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationLlmRequestMessageToolCall extends EntityBase
{
    /**
     * Database ID of the historical assistant request message containing this Tool call.
     */
    private long requestMessageId;

    /**
     * Zero-based position among Tool calls in the request message.
     */
    private int callOrder;

    /**
     * Provider-generated Tool call ID.
     */
    private String toolCallId;

    /**
     * Provider protocol Tool call type, normally function.
     */
    private String type;

    /**
     * Provider-facing function name requested by the historical assistant message.
     */
    private String functionName;

    /**
     * Exact JSON arguments emitted for the Tool call.
     */
    private String arguments;
}
