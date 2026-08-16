package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import ifl.agentbreaker.conversationmanager.domain.constants.ToolDispatchState;
import lombok.Data;

import java.time.Instant;

@Data
public class ConversationToolDispatch
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
