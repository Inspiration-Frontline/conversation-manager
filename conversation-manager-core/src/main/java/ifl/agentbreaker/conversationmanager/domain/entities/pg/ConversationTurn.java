package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import ifl.agentbreaker.conversationmanager.domain.constants.ConversationTurnStatus;
import ifl.agentbreaker.conversationmanager.domain.constants.LlmMessageStorageMode;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

/**
 * One LLM call and all Tool executions triggered by that call.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationTurn extends EntityBase
{
    /**
     * Database ID of the containing Round.
     */
    private long roundId;

    /**
     * Positive continuous sequence number within the Round.
     */
    private long turnNumber;

    /**
     * Stable numeric ID of the Agent definition used for this Turn.
     */
    private long agentId;

    /**
     * Runtime and handoff name of the resolved Agent definition.
     */
    private String agentName;

    /**
     * Positive version of the resolved Agent definition.
     */
    private int agentVersion;

    /**
     * Terminal status of the Turn.
     */
    private ConversationTurnStatus status;

    /**
     * Failure message; empty for a completed Turn.
     */
    private String errorMessage;

    /**
     * UTC instant at which Turn processing started.
     */
    private Instant startTime;

    /**
     * UTC instant at which Turn processing finished.
     */
    private Instant endTime;

    private Instant llmStartTime;

    private Instant llmEndTime;

    private String requestId;

    private String traceId;

    private LlmMessageStorageMode messageStorageMode;

    private String requestMessagesSnapshot;

    private String requestMessagesSnapshotHash;

    private String rawRequest;

    private boolean responseMessagePresent;

    private String responseContent;

    private String responseContentParts;

    private String finishReason;

    private boolean usagePresent;

    private Long promptTokens;

    private Long completionTokens;

    private Long totalTokens;

    private Long cachedPromptTokens;

    private Long reasoningTokens;

    private String rawResponse;

    private String responseErrorMessage;

    private String reasoningContent;
}
