package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import ifl.agentbreaker.conversationmanager.domain.constants.ToolCallExecutionStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationToolCallExecution extends EntityBase
{
    private long turnId;
    private long responseToolCallId;
    private int executionOrder;

    /**
     * Globally unique and permanently stable identity of the executed Tool.
     */
    private String toolKey;

    private ToolCallExecutionStatus status;
    private String resultContent;
    private String resultContentParts;
    private String rawResult;
    private String errorMessage;
    private Date startTime;
    private Date endTime;
}
