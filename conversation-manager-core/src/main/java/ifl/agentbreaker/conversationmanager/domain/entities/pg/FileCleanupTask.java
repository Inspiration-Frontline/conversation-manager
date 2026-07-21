package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import ifl.agentbreaker.conversationmanager.domain.constants.FileCleanupReason;
import ifl.agentbreaker.conversationmanager.domain.constants.FileTaskExecutionStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

@Data
@EqualsAndHashCode(callSuper = true)
public class FileCleanupTask extends EntityBase
{
    private long fileResourceId;
    private FileCleanupReason reason;
    private FileTaskExecutionStatus status;
    private int attempt;
    private Instant executeAfter;
    private String leaseToken;
    private Instant leaseUntil;
    private String lastError;
}
