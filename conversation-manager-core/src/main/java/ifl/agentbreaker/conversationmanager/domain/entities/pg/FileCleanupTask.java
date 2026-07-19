package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import ifl.agentbreaker.conversationmanager.domain.constants.FileCleanupReason;
import ifl.agentbreaker.conversationmanager.domain.constants.FileTaskExecutionStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = true)
public class FileCleanupTask extends EntityBase
{
    private long fileResourceId;
    private FileCleanupReason reason;
    private FileTaskExecutionStatus status;
    private int attempt;
    private Date executeAfter;
    private String leaseToken;
    private Date leaseUntil;
    private String lastError;
}
