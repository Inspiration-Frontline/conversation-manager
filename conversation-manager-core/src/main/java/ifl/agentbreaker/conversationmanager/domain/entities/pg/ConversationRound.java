package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import ifl.agentbreaker.conversationmanager.domain.constants.ConversationRoundStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationRound extends EntityBase
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
    private Date startTime;
    private Date endTime;
    private short payloadHashVersion;
    private String payloadHash;
    private boolean deleted;
    private Date deletionTime;
    private Long deletedBy;
}
