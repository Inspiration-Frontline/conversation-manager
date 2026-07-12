package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import ifl.agentbreaker.conversationmanager.domain.constants.ToolCallExecutionStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationToolCallExecution extends ExecutionEntityBase
{
    private long turnId;
    private long responseToolCallId;
    private int executionOrder;
    private String toolId;
    private ToolCallExecutionStatus status;
    private String resultContent;
    private String resultContentParts;
    private String rawResult;
    private String errorMessage;
    private long startTimeMs;
    private long endTimeMs;
}
