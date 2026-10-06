package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import lombok.Data;

import java.time.Instant;

/**
 * Terminal mutation applied to one bounded Task-Agent execution detail. Carrying the values as one
 * object keeps the mapper call readable and prevents positional argument mistakes as the audit
 * fields grow.
 */
@Data
public class TaskAgentExecutionTerminalUpdate
{
    /** Internal execution row identity selecting the row to update. */
    private long executionId;

    /** Authenticated owner recorded as the modifier and required to match the execution creator. */
    private long userId;

    /** Terminal execution status written to the row. */
    private String status;

    /** Bounded provider request identifier, or an empty string when the provider returned none. */
    private String providerRequestId;

    /** Client-safe failure classification, or an empty string on success. */
    private String errorCode;

    /** Client-safe failure message, or an empty string on success. */
    private String errorMessage;

    /** Terminal time recorded for the execution. */
    private Instant endTime;
}
