package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import ifl.agentbreaker.conversationmanager.domain.constants.ToolCallExecutionStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

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
     * Database ID of the response Tool call that caused this execution.
     */
    private long responseToolCallId;

    /**
     * Zero-based reporting order among Tool executions in the Turn.
     */
    private int executionOrder;

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
    private Date startTime;

    /**
     * UTC instant at which Tool execution finished.
     */
    private Date endTime;
}
