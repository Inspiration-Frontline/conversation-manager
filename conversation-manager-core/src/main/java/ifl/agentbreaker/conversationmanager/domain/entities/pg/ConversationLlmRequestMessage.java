package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import ifl.agentbreaker.conversationmanager.domain.constants.LlmMessageRole;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationLlmRequestMessage extends EntityBase
{
    /**
     * Database ID of the LLM call containing this request message.
     */
    private long llmCallId;

    /**
     * Zero-based position in the normalized provider message array.
     */
    private int messageOrder;

    /**
     * Normalized role of the request message.
     */
    private LlmMessageRole role;

    /**
     * Text content when the message is text-only.
     */
    private String content;

    /**
     * JSON representation of multimodal content parts.
     */
    private String contentParts;

    /**
     * Provider Tool call ID answered by a TOOL-role message.
     */
    private String toolCallId;
}
