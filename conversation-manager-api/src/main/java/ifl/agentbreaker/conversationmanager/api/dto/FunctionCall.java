package ifl.agentbreaker.conversationmanager.api.dto;

import lombok.Data;

/** Function name and exact JSON arguments emitted by a model Tool call. */
@Data
public class FunctionCall
{
    /** Provider-visible function name. */
    private String name;
    /** Exact JSON argument object serialized as text. */
    private String arguments;
}
