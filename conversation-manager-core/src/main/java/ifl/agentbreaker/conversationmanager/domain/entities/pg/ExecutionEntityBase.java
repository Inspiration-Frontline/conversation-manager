package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import lombok.Data;

import java.util.Date;

@Data
public abstract class ExecutionEntityBase
{
    private long id;

    private Date creationTime;
}
