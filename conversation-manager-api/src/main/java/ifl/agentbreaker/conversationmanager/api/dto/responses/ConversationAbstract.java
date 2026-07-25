package ifl.agentbreaker.conversationmanager.api.dto.responses;

import lombok.Data;

import java.time.Instant;

@Data
public class ConversationAbstract
{
    private String conversationId;
    private String title;
    private boolean pinned;
    private String conversationGroupId;
    private String conversationGroupName;
    private Instant lastRoundUpdatedTime;
    private Instant creationTime;
    private Instant modificationTime;
}
