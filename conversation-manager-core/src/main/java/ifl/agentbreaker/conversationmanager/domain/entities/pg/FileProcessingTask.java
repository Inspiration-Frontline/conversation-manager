package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import ifl.agentbreaker.conversationmanager.domain.constants.FileTaskExecutionStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

@Data
@EqualsAndHashCode(callSuper = true)
public class FileProcessingTask extends EntityBase
{
    private long fileResourceId;
    private FileTaskExecutionStatus status;
    private int attempt;
    private Instant executeAfter;
    private String leaseToken;
    private Instant leaseUntil;
    private String lastError;
}
