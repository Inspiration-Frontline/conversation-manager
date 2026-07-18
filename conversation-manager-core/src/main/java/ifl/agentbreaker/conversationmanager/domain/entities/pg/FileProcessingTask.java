package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import ifl.agentbreaker.conversationmanager.domain.constants.FileTaskStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = true)
public class FileProcessingTask extends EntityBase
{
    private long fileResourceId;
    private FileTaskStatus status;
    private int attempt;
    private Date executeAfter;
    private String leaseToken;
    private Date leaseUntil;
    private String lastError;
}
