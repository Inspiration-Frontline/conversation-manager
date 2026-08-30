package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import ifl.agentbreaker.conversationmanager.domain.constants.ToolCallType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Historical Tool call embedded in one normalized Assistant request message.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationLlmRequestMessageToolCall extends EntityBase
{
    /**
     * Database ID of the containing Round, retained for bounded replay queries.
     */
    private long roundId;

    /**
     * Database ID of the containing Turn.
     */
    private long turnId;

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
     * Provider protocol shape of the historical Tool call.
     */
    private ToolCallType type;

    /**
     * Provider-facing function name requested by the historical assistant message.
     */
    private String functionName;

    /**
     * Exact JSON arguments emitted for the Tool call.
     */
    private String arguments;
}
