package ifl.agentbreaker.conversationmanager.services.round;

import ifl.agentbreaker.conversationmanager.rpc.*;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ConversationRoundValidator
{
    public void validatePhaseThree(SaveConversationRoundRequest request)
    {
        require(request.getUserId() > 0, "user_id must be positive.");
        require(StringUtils.hasText(request.getConversationId()), "conversation_id is required.");
        require(request.getRoundNumber() > 0, "round_number must be positive.");
        require(request.hasUserRequest() && StringUtils.hasText(request.getUserRequest().getContent()),
            "Phase 3 requires a text user request.");
        require(request.getUserRequest().getContentPartsCount() == 0,
            "Phase 3 does not accept multimodal user requests.");
        require(request.getStatus() == RoundStatus.ROUND_STATUS_COMPLETED,
            "Phase 3 persists completed rounds only.");
        require(request.getErrorMessage().isEmpty(), "A completed round cannot contain an error.");
        require(request.hasFinalAnswer() && StringUtils.hasText(request.getFinalAnswer().getContent()),
            "A completed round requires a text final answer.");
        require(request.getFinalAnswer().getContentPartsCount() == 0
                && request.getFinalAnswer().getSourceTurnNumber() == 1,
            "The final answer must reference turn 1.");
        require(request.getTurnsCount() == 1, "Phase 3 requires exactly one turn.");
        requireTime(request.getStartTime(), request.getEndTime(), "round");

        ConversationTurn turn = request.getTurns(0);
        require(turn.getTurnNumber() == 1, "The only turn must be numbered 1.");
        require(turn.getStatus() == TurnStatus.TURN_STATUS_COMPLETED && turn.getErrorMessage().isEmpty(),
            "The Phase 3 turn must be completed without an error.");
        require(turn.hasAgentIdentity()
                && turn.getAgentIdentity().getAgentId() > 0
                && StringUtils.hasText(turn.getAgentIdentity().getName())
                && turn.getAgentIdentity().getVersion() > 0,
            "A resolved agent identity is required.");
        require(turn.getToolCallExecutionsCount() == 0, "Phase 3 does not execute tools.");
        requireTime(turn.getStartTime(), turn.getEndTime(), "turn");
        require(turn.getStartTime() >= request.getStartTime() && turn.getEndTime() <= request.getEndTime(),
            "Turn timing must be contained by round timing.");
        validateLlmCall(turn.getLlmCall(), turn);
    }

    private void validateLlmCall(LlmCall call, ConversationTurn turn)
    {
        require(call != null && call.hasRequest() && call.hasResponse(), "The turn requires one LLM call.");
        requireTime(call.getStartTime(), call.getEndTime(), "LLM call");
        require(call.getStartTime() >= turn.getStartTime() && call.getEndTime() <= turn.getEndTime(),
            "LLM call timing must be contained by turn timing.");

        LlmRequest request = call.getRequest();
        require(StringUtils.hasText(request.getProvider()) && StringUtils.hasText(request.getModel()),
            "LLM provider and model are required.");
        require(request.getMessageStorageMode() == LlmMessageStorageMode.LLM_MESSAGE_STORAGE_MODE_FULL_SNAPSHOT,
            "Turn 1 must use FULL_SNAPSHOT storage.");
        require(request.getMessagesCount() >= 1, "The LLM request requires normalized messages.");
        require(request.getToolsCount() == 0, "Phase 3 does not expose tools to the model.");
        for (LlmConversationMessage message : request.getMessagesList())
        {
            require(message.getRole() != MessageRole.MESSAGE_ROLE_UNSPECIFIED,
                "Every request message requires a role.");
            require(message.getContentPartsCount() == 0 && message.getToolCallsCount() == 0
                    && message.getToolCallId().isEmpty(),
                "Phase 3 request messages must be plain text without tool metadata.");
            require(StringUtils.hasText(message.getContent()), "Phase 3 request messages require text content.");
        }

        LlmResponse response = call.getResponse();
        require(response.hasMessage() && StringUtils.hasText(response.getMessage().getContent()),
            "The LLM response requires text content.");
        require(response.getMessage().getContentPartsCount() == 0 && response.getMessage().getToolCallsCount() == 0,
            "Phase 3 LLM responses cannot contain multimodal content or tool calls.");
        require(StringUtils.hasText(response.getFinishReason()) && response.getErrorMessage().isEmpty(),
            "A successful LLM response requires a finish reason and no error.");
        if (response.hasUsage())
        {
            TokenUsage usage = response.getUsage();
            require(usage.getPromptTokens() >= 0 && usage.getCompletionTokens() >= 0
                    && usage.getTotalTokens() >= 0 && usage.getCachedPromptTokens() >= 0
                    && usage.getReasoningTokens() >= 0,
                "Token usage cannot be negative.");
        }
    }

    private void requireTime(long start, long end, String label)
    {
        require(start > 0 && end >= start, label + " timing is invalid.");
    }

    private void require(boolean condition, String message)
    {
        if (!condition)
            throw new RoundPersistenceException(
                ConversationErrorCode.CONVERSATION_ERROR_CODE_INVALID_REQUEST_VALUE, message);
    }
}
