package ifl.agentbreaker.conversationmanager.api.dto.responses;

import lombok.Data;

import java.util.Date;

@Data
public class ConversationAbstract
{
    private String conversationId;

    private String title;

    private Date creationTime;

    private Date modificationTime;
}
