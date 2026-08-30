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

    /**
     * UTC instant immediately before the provider model invocation.
     */
    private Instant llmStartTime;

    /**
     * UTC instant immediately after the provider model invocation.
     */
    private Instant llmEndTime;

    /**
     * Provider request identifier returned for this invocation, when available.
     */
    private String requestId;

    /**
     * Lowercase W3C trace identifier shared with the containing Round.
     */
    private String traceId;

    /**
     * Persistence mode for normalized request messages in this Turn.
     */
    private LlmMessageStorageMode messageStorageMode;

    /**
     * Read-optimized JSON projection of the normalized request messages.
     */
    private String requestMessagesSnapshot;

    /**
     * SHA-256 digest of the normalized JSON request snapshot.
     */
    private String requestMessagesSnapshotHash;

    /**
     * Redacted raw provider request retained for diagnostics when configured.
     */
    private String rawRequest;

    /**
     * Whether the provider returned an assistant response message.
     */
    private boolean responseMessagePresent;

    /**
     * Plain-text assistant response content, when the response is text-only.
     */
    private String responseContent;

    /**
     * JSON representation of structured assistant response content parts.
     */
    private String responseContentParts;

    /**
     * Provider finish reason for the model invocation.
     */
    private String finishReason;

    /**
     * Whether all token-usage counters are present and meaningful.
     */
    private boolean usagePresent;

    /**
     * Prompt token count; null when the provider did not return usage.
     */
    private Long promptTokens;

    /**
     * Completion token count; null when the provider did not return usage.
     */
    private Long completionTokens;

    /**
     * Total token count; null when the provider did not return usage.
     */
    private Long totalTokens;

    /**
     * Cached prompt token count; null when the provider did not return usage.
     */
    private Long cachedPromptTokens;

    /**
     * Reasoning token count; null when the provider did not return usage.
     */
    private Long reasoningTokens;

    /**
     * Redacted raw provider response retained for diagnostics when configured.
     */
    private String rawResponse;

    /**
     * Provider error text captured alongside an unsuccessful response.
     */
    private String responseErrorMessage;

    /**
     * Provider reasoning content retained separately from visible assistant content.
     */
    private String reasoningContent;
}
