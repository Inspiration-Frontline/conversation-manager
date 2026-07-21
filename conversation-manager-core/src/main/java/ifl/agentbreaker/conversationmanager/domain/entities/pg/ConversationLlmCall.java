package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import ifl.agentbreaker.conversationmanager.domain.constants.LlmMessageStorageMode;
import ifl.agentbreaker.conversationmanager.domain.constants.ToolChoiceMode;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

/**
 * Persisted request and response snapshot for the single LLM call in a Turn.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationLlmCall extends EntityBase
{
    /**
     * Database ID of the containing Turn.
     */
    private long turnId;

    /**
     * LLM provider identifier used for this call.
     */
    private String provider;

    /**
     * Provider model identifier used for this call.
     */
    private String model;

    /**
     * Provider request ID, or an empty string when unavailable.
     */
    private String requestId;

    /**
     * Distributed trace ID associated with this call.
     */
    private String traceId;

    /**
     * Whether request messages are a full snapshot or an append delta.
     */
    private LlmMessageStorageMode messageStorageMode;

    /**
     * Whether a Tool choice was explicitly supplied to the provider.
     */
    private boolean toolChoicePresent;

    /**
     * Normalized Tool selection mode when Tool choice is present.
     */
    private ToolChoiceMode toolChoiceMode;

    /**
     * Provider-facing Tool name when one specific Tool is selected.
     */
    private String toolChoiceName;

    /**
     * Exact normalized response-format configuration.
     */
    private String responseFormat;

    /**
     * Sampling temperature, or null when omitted.
     */
    private Double temperature;

    /**
     * Maximum requested output tokens, or null when omitted.
     */
    private Long maxOutputTokens;

    /**
     * Optional retained raw provider request after redaction.
     */
    private String rawRequest;

    /**
     * UTC instant at which the provider call started.
     */
    private Instant startTime;

    /**
     * UTC instant at which the provider call finished.
     */
    private Instant endTime;

    /**
     * Whether the provider returned a normalized assistant message.
     */
    private boolean responseMessagePresent;

    /**
     * Text response content when the response is text-only.
     */
    private String responseContent;

    /**
     * JSON representation of multimodal response content parts.
     */
    private String responseContentParts;

    /**
     * Provider-normalized reason why generation stopped.
     */
    private String finishReason;

    /**
     * Whether token-usage values were returned by the provider.
     */
    private boolean usagePresent;

    /**
     * Number of input or prompt tokens.
     */
    private Long promptTokens;

    /**
     * Number of generated completion tokens.
     */
    private Long completionTokens;

    /**
     * Total token count reported by the provider.
     */
    private Long totalTokens;

    /**
     * Number of prompt tokens served from provider cache.
     */
    private Long cachedPromptTokens;

    /**
     * Number of tokens attributed to model reasoning.
     */
    private Long reasoningTokens;

    /**
     * Optional retained raw provider response after redaction.
     */
    private String rawResponse;

    /**
     * Provider-call error; empty when the call succeeded.
     */
    private String responseErrorMessage;

    /**
     * Normalized reasoning text kept separate from user-visible content.
     */
    private String reasoningContent;
}
