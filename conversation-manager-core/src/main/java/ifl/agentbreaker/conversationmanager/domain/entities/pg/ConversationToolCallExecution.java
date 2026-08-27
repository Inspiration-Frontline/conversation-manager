package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import ifl.agentbreaker.conversationmanager.domain.constants.ToolCallExecutionStatus;
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

    private long roundId;

    private int callOrder;

    private String toolCallId;

    private String type;

    private String toolName;

    private String arguments;

    /**
     * Zero-based reporting order among Tool executions in the Turn.
     */
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
