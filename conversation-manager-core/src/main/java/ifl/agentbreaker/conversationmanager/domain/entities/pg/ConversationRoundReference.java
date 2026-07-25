package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationRoundReference extends EntityBase
{
    private long roundId;
    private String sourceConversationId;
    private long sourceEndRoundNumber;
    private String sourceTitle;
    private int referenceOrder;
}
