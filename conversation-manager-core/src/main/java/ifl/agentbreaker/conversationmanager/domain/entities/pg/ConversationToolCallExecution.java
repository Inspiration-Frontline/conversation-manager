package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import ifl.agentbreaker.conversationmanager.domain.constants.ToolCallExecutionStatus;
import ifl.agentbreaker.conversationmanager.domain.constants.ToolCallType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

/**
 * Persisted outcome of executing one Tool call emitted by the current LLM response.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationToolCallExecution extends EntityBase
{
    /**
     * Database ID of the containing Turn.
     */
    private long turnId;

    /**
     * Database ID of the containing Round, retained for direct diagnostics.
     */
    private long roundId;

    /**
     * Zero-based position of the emitted Tool call in the model response.
     */
    private int callOrder;

    /**
     * Provider-generated Tool call identifier.
     */
    private String toolCallId;

    /**
     * Provider protocol shape of the emitted Tool call.
     */
    private ToolCallType type;

    /**
     * Provider-visible function name used by the model response.
     */
    private String toolName;

    /**
     * Exact JSON arguments emitted for the Tool call.
     */
    private String arguments;

    /**
     * Globally unique and permanently stable identity of the executed Tool.
     */
    private String toolKey;

    /**
     * Terminal execution status.
     */
    private ToolCallExecutionStatus status;

    /**
     * Normalized text result supplied to subsequent model context.
     */
    private String resultContent;

    /**
     * JSON representation of a normalized multimodal Tool result.
     */
    private String resultContentParts;

    /**
     * Optional retained raw Tool result after redaction.
     */
    private String rawResult;

    /**
     * Failure message; empty for a completed execution.
     */
    private String errorMessage;

    /**
     * UTC instant at which Tool execution started.
     */
    private Instant startTime;

    /**
     * UTC instant at which Tool execution finished.
     */
    private Instant endTime;
}
