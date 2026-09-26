package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import ifl.agentbreaker.conversationmanager.domain.constants.GenerationAttemptStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

/** Audit record for one idempotent provider generation attempt. */
@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationGenerationAttempt extends EntityBase
{
    /** Stable public idempotency identity supplied by Runner. */
    private String attemptId;
    /** Database identity of the containing Round. */
    private long roundId;
    /** Capability that initiated the attempt. */
    private String capabilityKey;
    /** Canonical model identifier selected for this attempt. */
    private String model;
    /** Current durable attempt state. */
    private GenerationAttemptStatus status;
    /** Optional bounded provider request identifier. */
    private String providerRequestId;
    /** Client-safe failure classification. */
    private String errorCode;
    /** Bounded client-safe failure message. */
    private String errorMessage;
    /** Provider dispatch start time. */
    private Instant startTime;
    /** Terminal time, or null while the attempt is active. */
    private Instant endTime;
}
