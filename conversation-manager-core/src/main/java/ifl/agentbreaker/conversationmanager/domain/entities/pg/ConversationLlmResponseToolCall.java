package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationLlmResponseToolCall extends EntityBase
{
    /**
     * Database ID of the containing Turn.
     */
    private long turnId;

    /**
     * Database ID of the LLM call that emitted this Tool call.
     */
    private long llmCallId;

    /**
     * Zero-based position among Tool calls in the LLM response.
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
     * Provider-facing function name selected by the model.
     */
    private String functionName;

    /**
     * Exact JSON arguments emitted by the model.
     */
    private String arguments;
}
