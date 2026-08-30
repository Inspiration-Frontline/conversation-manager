package ifl.agentbreaker.conversationmanager.api.dto;

import lombok.Data;

/** Provider-neutral model Tool call in the legacy Java RPC DTO contract. */
@Data
public class ToolCall
{
    /** Provider-generated Tool call identifier. */
    private String id;
    /** Provider protocol call shape, currently {@code function}. */
    private String type;
    /** Function metadata emitted by the model. */
    private FunctionCall function;
}
