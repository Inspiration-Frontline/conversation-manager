package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import ifl.agentbreaker.conversationmanager.domain.constants.ConversationRoundStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationRound extends ExecutionEntityBase
{
    private String conversationId;
    private long roundNumber;
    private String userRequestContent;
    private String userRequestContentParts;
    private String finalAnswerContent;
    private String finalAnswerContentParts;
    private Long finalSourceTurnNumber;
    private ConversationRoundStatus status;
    private String errorMessage;
    private long startTimeMs;
    private long endTimeMs;
    private short payloadHashVersion;
    private String payloadHash;
    private boolean deleted;
    private Date deletionTime;
    private Long deletedBy;
    private Date modificationTime;
}
