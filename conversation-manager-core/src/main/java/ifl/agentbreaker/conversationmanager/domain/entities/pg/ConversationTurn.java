package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import ifl.agentbreaker.conversationmanager.domain.constants.ConversationTurnStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationTurn extends ExecutionEntityBase
{
    private long roundId;
    private long turnNumber;
    private long agentId;
    private String agentName;
    private int agentVersion;
    private ConversationTurnStatus status;
    private String errorMessage;
    private long startTimeMs; // TODO: Why don't we use Date?
    private long endTimeMs; // TODO: Why don't we use Date?
}
