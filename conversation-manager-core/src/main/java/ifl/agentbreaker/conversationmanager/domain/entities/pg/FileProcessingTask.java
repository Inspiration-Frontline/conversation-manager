package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import ifl.agentbreaker.conversationmanager.domain.constants.FileTaskExecutionStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

/** Durable retry task that extracts and scans one uploaded file resource. */
@Data
@EqualsAndHashCode(callSuper = true)
public class FileProcessingTask extends EntityBase
{
    /** Stable identifier of the file resource. */
    private long fileResourceId;
    /** Current lifecycle or execution status. */
    private FileTaskExecutionStatus status;
    /** Number of processing attempts made for this resource. */
    private int attempt;
    /** Earliest time at which a worker may claim the task. */
    private Instant executeAfter;
    /** Worker lease token guarding state transitions. */
    private String leaseToken;
    /** Lease expiry after which another worker may reclaim the task. */
    private Instant leaseUntil;
    /** Bounded diagnostic from the last failed attempt. */
    private String lastError;
}
