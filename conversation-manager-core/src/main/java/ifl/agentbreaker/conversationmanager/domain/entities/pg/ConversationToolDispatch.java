package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import ifl.agentbreaker.conversationmanager.domain.constants.ToolDispatchState;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

/**
 * Durable evidence for one remote MCP Tool delivery attempt.
 *
 * <p>The row is created before delivery and later advances to one terminal state. It lets recovery
 * distinguish a definite pre-delivery failure from an outcome that is unknown after a transport
 * interruption, where automatically retrying could execute a side effect twice.</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationToolDispatch extends EntityBase
{
    private long roundId;
    private String attemptId;
    private long turnNumber;
    private String toolCallId;
    private String toolName;
    private String toolKey;
    private String serverId;
    private String argumentsJson;
    private ToolDispatchState state;
    private Instant dispatchTime;
    private Instant resultTime;
    private String traceId;
    private String spanId;
    private String transportEvidence;
    private String recoveryReason;
}
