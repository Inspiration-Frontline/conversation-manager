package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationRoundFile extends EntityBase
{
    private long roundId;
    private long fileResourceId;
    private int fileOrder;
}
