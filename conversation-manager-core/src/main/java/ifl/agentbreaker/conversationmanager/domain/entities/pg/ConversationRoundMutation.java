package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import lombok.Data;

@Data
public class ConversationRoundMutation
{
    private long id;
    private long roundId;
    private String mutationId;
    private String payloadHash;
    private long committedRevision;
}
