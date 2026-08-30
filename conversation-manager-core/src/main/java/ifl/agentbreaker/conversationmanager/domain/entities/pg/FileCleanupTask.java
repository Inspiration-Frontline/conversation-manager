package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import ifl.agentbreaker.conversationmanager.domain.constants.FileCleanupReason;
import ifl.agentbreaker.conversationmanager.domain.constants.FileTaskExecutionStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

/** Durable retry task that removes an unreferenced file object from storage. */
@Data
@EqualsAndHashCode(callSuper = true)
public class FileCleanupTask extends EntityBase
{
    /** Stable identifier of the file resource. */
    private long fileResourceId;
    /** Business reason that physical cleanup was scheduled. */
    private FileCleanupReason reason;
    /** Current lifecycle or execution status. */
    private FileTaskExecutionStatus status;
    /** Number of cleanup attempts made for this resource. */
    private int attempt;
    /** Earliest time at which a worker may claim the task. */
    private Instant executeAfter;
    /** Worker lease token guarding cleanup transitions. */
    private String leaseToken;
    /** Lease expiry after which another worker may reclaim the task. */
    private Instant leaseUntil;
    /** Bounded diagnostic from the last failed cleanup attempt. */
    private String lastError;
}
