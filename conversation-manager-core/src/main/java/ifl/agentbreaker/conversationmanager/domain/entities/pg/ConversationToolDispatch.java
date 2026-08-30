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
    /** Database identifier of the containing Round. */
    private long roundId;
    /** Stable identifier of the attempt. */
    private String attemptId;
    /** Numeric turn number used for ordering or bounds. */
    private long turnNumber;
    /** Provider-generated Tool call identifier. */
    private String toolCallId;
    /** Provider-visible Tool name selected by the model. */
    private String toolName;
    /** Stable AgentBreaker Tool identity. */
    private String toolKey;
    /** Stable identifier of the server. */
    private String serverId;
    /** Redacted JSON arguments delivered to the Tool. */
    private String argumentsJson;
    /** Durable dispatch state, including uncertain delivery. */
    private ToolDispatchState state;
    /** UTC instant marking dispatch time. */
    private Instant dispatchTime;
    /** UTC instant marking result time. */
    private Instant resultTime;
    /** Stable identifier of the trace. */
    private String traceId;
    /** Stable identifier of the span. */
    private String spanId;
    /** Bounded transport diagnostic retained for recovery analysis. */
    private String transportEvidence;
    /** Explanation for a recovery or retry transition. */
    private String recoveryReason;
}
