package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import ifl.agentbreaker.conversationmanager.domain.constants.TaskAgentExecutionStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

/**
 * Bounded audit detail for a specialized Task-Agent invocation.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationTaskAgentExecution extends EntityBase
{
    /**
     * Database identity of the containing Round.
     */
    private long roundId;

    /**
     * Optional parent primary-agent Turn identity.
     */
    private Long parentTurnId;

    /**
     * Stable Task-Agent catalog identity.
     */
    private long taskAgentId;

    /**
     * Task-Agent display name captured at execution time.
     */
    private String taskAgentName;

    /**
     * Immutable Task-Agent definition version.
     */
    private int taskAgentVersion;

    /**
     * Capability that initiated the execution.
     */
    private String capabilityKey;

    /**
     * Current execution state.
     */
    private TaskAgentExecutionStatus status;
    /**
     * Request correlation identifier.
     */
    private String requestId;

    /**
     * W3C trace identifier.
     */
    private String traceId;

    /**
     * Parent span identifier for child-span diagnostics.
     */
    private String parentSpanId;

    /**
     * Task-Agent span identifier.
     */
    private String taskSpanId;

    /**
     * Bounded primary-to-task rewrite.
     */
    private String rewrittenInstruction;

    /**
     * JSON array of stable input resource IDs.
     */
    private String inputResourceIdsJson;

    /**
     * JSON object of normalized settings.
     */
    private String normalizedSettingsJson;

    /**
     * Optional generation attempt identity.
     */
    private Long generationAttemptId;

    /**
     * Optional bounded provider request identifier.
     */
    private String providerRequestId;

    /**
     * Client-safe failure classification.
     */
    private String errorCode;

    /**
     * Bounded client-safe failure message.
     */
    private String errorMessage;

    /**
     * Execution start time.
     */
    private Instant startTime;

    /**
     * Terminal time, or null while running.
     */
    private Instant endTime;
}
