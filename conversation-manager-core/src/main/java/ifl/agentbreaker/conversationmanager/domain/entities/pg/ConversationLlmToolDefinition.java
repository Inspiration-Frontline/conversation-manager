package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import ifl.agentbreaker.conversationmanager.domain.constants.ToolSourceType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Immutable audit snapshot of one Tool definition actually offered in an LLM request.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationLlmToolDefinition extends EntityBase
{
    /**
     * Database ID of the LLM call that used this frozen definition.
     */
    private long llmCallId;

    /**
     * Zero-based position of the Tool in the provider request.
     */
    private int toolOrder;

    /**
     * Globally unique and permanently stable Tool identity declared by agent-runner code.
     */
    private String toolKey;

    /**
     * Function name exposed to the LLM; unique only within the containing LLM request.
     */
    private String toolName;

    /**
     * Origin from which agent-runner resolves and executes the Tool.
     */
    private ToolSourceType sourceType;

    /**
     * Human-readable Tool description sent to the LLM.
     */
    private String description;

    /**
     * Exact JSON Schema text sent as the Tool's accepted argument definition.
     */
    private String parametersJson;

    /**
     * Whether the provider should enforce strict JSON Schema output for Tool arguments.
     */
    private boolean strict;

    /**
     * Lowercase SHA-256 digest of the canonical normalized Tool definition.
     */
    private String definitionHash;
}
