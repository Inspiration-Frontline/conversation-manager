package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import lombok.Data;

import java.time.Instant;

/**
 * Terminal mutation applied to one idempotent generation attempt. Carrying the values as one object
 * keeps the mapper call readable and prevents positional argument mistakes as the audit fields grow.
 */
@Data
public class GenerationAttemptTerminalUpdate
{
    /** Stable Runner-supplied attempt identity selecting the row to update. */
    private String attemptId;

    /** Authenticated owner recorded as the modifier and required to match the attempt creator. */
    private long userId;

    /** Terminal attempt status written to the row. */
    private String status;

    /** Bounded provider request identifier, or an empty string when the provider returned none. */
    private String providerRequestId;

    /** Client-safe failure classification, or an empty string on success. */
    private String errorCode;

    /** Client-safe failure message, or an empty string on success. */
    private String errorMessage;

    /** Terminal time recorded for the attempt. */
    private Instant endTime;
}
