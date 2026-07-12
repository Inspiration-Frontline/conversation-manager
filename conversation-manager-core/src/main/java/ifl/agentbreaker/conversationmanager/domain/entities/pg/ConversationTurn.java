package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import ifl.agentbreaker.conversationmanager.domain.constants.ConversationTurnStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationTurn extends EntityBase
{
    private long roundId;
    private long turnNumber;
    private long agentId;
    private String agentName;
    private int agentVersion;
    private ConversationTurnStatus status;
    private String errorMessage;
    private Date startTime;
    private Date endTime;
}
