package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import ifl.agentbreaker.conversationmanager.domain.constants.LlmMessageRole;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Normalized source-of-truth row for one ordered message sent in a model Turn. */
@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationLlmRequestMessage extends EntityBase
{
    /** Database identifier of the containing Round. */
    private long roundId;

    /** Database identifier of the containing Turn. */
    private long turnId;

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
