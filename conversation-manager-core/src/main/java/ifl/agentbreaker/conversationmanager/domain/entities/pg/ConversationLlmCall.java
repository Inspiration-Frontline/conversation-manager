package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import ifl.agentbreaker.conversationmanager.domain.constants.LlmMessageStorageMode;
import ifl.agentbreaker.conversationmanager.domain.constants.ToolChoiceMode;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationLlmCall extends ExecutionEntityBase
{
    private long turnId;
    private String provider;
    private String model;
    private String requestId;
    private String traceId;
    private LlmMessageStorageMode messageStorageMode;
    private boolean toolChoicePresent;
    private ToolChoiceMode toolChoiceMode;
    private String toolChoiceName;
    private String responseFormat;
    private Double temperature;
    private Long maxOutputTokens;
    private String rawRequest;
    private long startTimeMs; // TODO: Why don't we use Date?
    private long endTimeMs; // TODO: Why don't we use Date?
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
}
