package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import ifl.agentbreaker.conversationmanager.domain.constants.LlmMessageStorageMode;
import ifl.agentbreaker.conversationmanager.domain.constants.ToolChoiceMode;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationLlmCall extends EntityBase
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
    private Date startTime;
    private Date endTime;
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
