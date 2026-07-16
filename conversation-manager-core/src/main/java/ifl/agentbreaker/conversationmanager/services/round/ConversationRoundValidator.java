package ifl.agentbreaker.conversationmanager.services.round;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ifl.agentbreaker.commons.api.dto.AgentIdentity;
import ifl.agentbreaker.conversationmanager.rpc.AssistantMessage;
import ifl.agentbreaker.conversationmanager.rpc.ConversationErrorCode;
import ifl.agentbreaker.conversationmanager.rpc.ConversationTurn;
import ifl.agentbreaker.conversationmanager.rpc.LlmCall;
import ifl.agentbreaker.conversationmanager.rpc.LlmConversationMessage;
import ifl.agentbreaker.conversationmanager.rpc.LlmMessageStorageMode;
import ifl.agentbreaker.conversationmanager.rpc.LlmRequest;
import ifl.agentbreaker.conversationmanager.rpc.LlmResponse;
import ifl.agentbreaker.conversationmanager.rpc.MessageRole;
import ifl.agentbreaker.conversationmanager.rpc.RoundStatus;
import ifl.agentbreaker.conversationmanager.rpc.SaveConversationRoundRequest;
import ifl.agentbreaker.conversationmanager.rpc.TokenUsage;
import ifl.agentbreaker.conversationmanager.rpc.ToolCall;
import ifl.agentbreaker.conversationmanager.rpc.ToolCallExecution;
import ifl.agentbreaker.conversationmanager.rpc.ToolCallExecutionStatus;
import ifl.agentbreaker.conversationmanager.rpc.ToolDefinition;
import ifl.agentbreaker.conversationmanager.rpc.ToolSourceType;
import ifl.agentbreaker.conversationmanager.rpc.TurnStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ConversationRoundValidator
{
    @Autowired
    private ObjectMapper objectMapper;

    // TODO: Rename to validate() once the remaining Round/Turn phases share this contract.
    public void validatePhaseFour(SaveConversationRoundRequest request)
    {
        require(request.getUserId() > 0, "user_id must be positive.");
        require(StringUtils.hasText(request.getConversationId()), "conversation_id is required.");
        require(request.getRoundNumber() > 0, "round_number must be positive.");
        require(request.hasUserRequest() && StringUtils.hasText(request.getUserRequest().getContent()),
            "A text user request is required.");
        require(request.getUserRequest().getContentPartsCount() == 0,
            "Multimodal user requests are not supported yet.");
        require(request.getStatus() != RoundStatus.ROUND_STATUS_UNSPECIFIED,
            "A terminal round status is required.");
        requireTime(request.getStartTime(), request.getEndTime(), "round");

        if (request.getStatus() != RoundStatus.ROUND_STATUS_COMPLETED)
        {
            require(!request.hasFinalAnswer(), "A failed or cancelled round cannot contain a final answer.");
            if (request.getStatus() == RoundStatus.ROUND_STATUS_FAILED)
                require(StringUtils.hasText(request.getErrorMessage()), "A failed round requires an error message.");
            validateTurns(request, false);
            return;
        }

        require(request.getErrorMessage().isEmpty(), "A completed round cannot contain an error.");
        require(request.hasFinalAnswer() && StringUtils.hasText(request.getFinalAnswer().getContent()),
            "A completed round requires a text final answer.");
        require(request.getFinalAnswer().getContentPartsCount() == 0,
            "Multimodal final answers are not supported yet.");
        require(request.getTurnsCount() >= 1, "A completed round requires at least one turn.");
        require(request.getFinalAnswer().getSourceTurnNumber() == request.getTurnsCount(),
            "The final answer must reference the last turn.");
        validateTurns(request, true);
        ConversationTurn finalTurn = request.getTurns(request.getTurnsCount() - 1);
        require(finalTurn.getLlmCall().getResponse().getMessage().getToolCallsCount() == 0,
            "The final turn cannot end with pending Tool calls.");
        require(request.getFinalAnswer().getContent().equals(
                finalTurn.getLlmCall().getResponse().getMessage().getContent()),
            "The final answer must match the last LLM response.");
    }

    private void validateTurns(SaveConversationRoundRequest request, boolean completedRound)
    {
        AgentIdentity firstIdentity = null;
        ConversationTurn previousTurn = null;
        for (int index = 0; index < request.getTurnsCount(); index++)
        {
            ConversationTurn turn = request.getTurns(index);
            boolean isLast = index == request.getTurnsCount() - 1;
            require(turn.getTurnNumber() == index + 1, "Turn numbers must be continuous from 1.");
            require(turn.hasAgentIdentity()
                    && turn.getAgentIdentity().getAgentId() > 0
                    && StringUtils.hasText(turn.getAgentIdentity().getName())
                    && turn.getAgentIdentity().getVersion() > 0,
                "A resolved agent identity is required.");
            if (firstIdentity == null)
                firstIdentity = turn.getAgentIdentity();
            else
                require(firstIdentity.equals(turn.getAgentIdentity()),
                    "The resolved Agent identity must remain fixed within one Round.");
            if (completedRound)
                require(turn.getStatus() == TurnStatus.TURN_STATUS_COMPLETED && turn.getErrorMessage().isEmpty(),
                    "Every turn in a completed round must be completed without an error.");
            else if (!isLast)
                require(turn.getStatus() == TurnStatus.TURN_STATUS_COMPLETED && turn.getErrorMessage().isEmpty(),
                    "Only the last partial turn may fail or be cancelled.");
            else
            {
                TurnStatus expectedStatus = request.getStatus() == RoundStatus.ROUND_STATUS_CANCELLED
                    ? TurnStatus.TURN_STATUS_CANCELLED : TurnStatus.TURN_STATUS_FAILED;
                require(turn.getStatus() == expectedStatus,
                    "The last partial turn status must match the Round status.");
                if (expectedStatus == TurnStatus.TURN_STATUS_FAILED)
                    require(StringUtils.hasText(turn.getErrorMessage()),
                        "A failed partial turn requires an error message.");
            }
            requireTime(turn.getStartTime(), turn.getEndTime(), "turn");
            require(turn.getStartTime() >= request.getStartTime() && turn.getEndTime() <= request.getEndTime(),
                "Turn timing must be contained by round timing.");
            validateLlmCall(turn.getLlmCall(), turn, index);
            if (previousTurn != null)
                validateContinuationDelta(previousTurn, turn.getLlmCall().getRequest());
            if (!isLast)
                require(turn.getLlmCall().getResponse().getMessage().getToolCallsCount() > 0,
                    "Every non-final Turn must continue through at least one Tool call.");
            previousTurn = turn;
        }
    }

    private void validateContinuationDelta(ConversationTurn previousTurn, LlmRequest currentRequest)
    {
        AssistantMessage previousMessage = previousTurn.getLlmCall().getResponse().getMessage();
        List<ToolCall> previousCalls = previousMessage.getToolCallsList();
        require(!previousCalls.isEmpty(), "APPEND_DELTA requires Tool calls from the preceding Turn.");
        require(currentRequest.getMessagesCount() == previousCalls.size() + 1,
            "APPEND_DELTA must contain the preceding Assistant Tool call message and every Tool result.");

        LlmConversationMessage assistantDelta = currentRequest.getMessages(0);
        require(assistantDelta.getRole() == MessageRole.MESSAGE_ROLE_ASSISTANT
                && assistantDelta.getContent().equals(previousMessage.getContent())
                && assistantDelta.getToolCallsCount() == previousCalls.size(),
            "APPEND_DELTA must start with the exact preceding Assistant Tool call message.");
        for (int index = 0; index < previousCalls.size(); index++)
            require(toolCallEquals(previousCalls.get(index), assistantDelta.getToolCalls(index)),
                "APPEND_DELTA Assistant Tool calls must match the preceding model response.");

        Map<String, ToolCallExecution> executionsById = new HashMap<>();
        for (ToolCallExecution execution : previousTurn.getToolCallExecutionsList())
            executionsById.put(execution.getToolCallId(), execution);
        for (int index = 0; index < previousCalls.size(); index++)
        {
            ToolCall previousCall = previousCalls.get(index);
            ToolCallExecution execution = executionsById.get(previousCall.getId());
            LlmConversationMessage toolDelta = currentRequest.getMessages(index + 1);
            require(execution != null
                    && execution.getStatus() != ToolCallExecutionStatus.TOOL_CALL_EXECUTION_STATUS_CANCELLED
                    && toolDelta.getRole() == MessageRole.MESSAGE_ROLE_TOOL
                    && toolDelta.getToolCallId().equals(previousCall.getId())
                    && toolDelta.getContent().equals(execution.getResultContent()),
                "Every APPEND_DELTA Tool message must exactly match its preceding execution result.");
        }
    }

    private boolean toolCallEquals(ToolCall first, ToolCall second)
    {
        return first.getId().equals(second.getId())
            && first.getType().equals(second.getType())
            && first.getFunction().getName().equals(second.getFunction().getName())
            && jsonEquals(first.getFunction().getArguments(), second.getFunction().getArguments());
    }

    private void validateLlmCall(LlmCall call, ConversationTurn turn, int turnIndex)
    {
        require(call != null && call.hasRequest() && call.hasResponse(), "The turn requires one LLM call.");
        requireTime(call.getStartTime(), call.getEndTime(), "LLM call");
        require(call.getStartTime() >= turn.getStartTime() && call.getEndTime() <= turn.getEndTime(),
            "LLM call timing must be contained by turn timing.");

        LlmRequest request = call.getRequest();
        require(StringUtils.hasText(request.getProvider()) && StringUtils.hasText(request.getModel()),
            "LLM provider and model are required.");
        require(request.getMessageStorageMode() == (turnIndex == 0
                ? LlmMessageStorageMode.LLM_MESSAGE_STORAGE_MODE_FULL_SNAPSHOT
                : LlmMessageStorageMode.LLM_MESSAGE_STORAGE_MODE_APPEND_DELTA),
            turnIndex == 0 ? "Turn 1 must use FULL_SNAPSHOT storage."
                : "Turns after turn 1 must use APPEND_DELTA storage.");
        require(request.getMessagesCount() >= 1, "The LLM request requires normalized messages.");

        Map<String, ToolDefinition> toolsByName = validateToolDefinitions(request);
        validateRequestMessages(request.getMessagesList());
        validateResponseAndExecutions(call.getResponse(), turn, toolsByName, call.getEndTime());
    }

    private Map<String, ToolDefinition> validateToolDefinitions(LlmRequest request)
    {
        Map<String, ToolDefinition> toolsByName = new HashMap<>();
        Set<String> toolKeys = new HashSet<>();
        for (ToolDefinition tool : request.getToolsList())
        {
            require(tool.getSourceType() != ToolSourceType.TOOL_SOURCE_TYPE_UNSPECIFIED,
                "Every Tool definition requires a source type.");
            require(StringUtils.hasText(tool.getToolKey()) && StringUtils.hasText(tool.getToolName()),
                "Every Tool definition requires a stable key and provider name.");
            require(StringUtils.hasText(tool.getParametersJson()) && isJson(tool.getParametersJson()),
                "Tool parameters_json must contain valid JSON.");
            require(tool.getDefinitionHash().matches("^[0-9a-f]{64}$"),
                "Tool definition_hash must be a lowercase SHA-256 digest.");
            require(toolKeys.add(tool.getToolKey()), "Tool keys must be unique within one LLM request.");
            require(toolsByName.put(tool.getToolName(), tool) == null,
                "Tool names must be unique within one LLM request.");
        }
        return toolsByName;
    }

    private void validateRequestMessages(Iterable<LlmConversationMessage> messages)
    {
        Set<String> priorToolCallIds = new HashSet<>();
        for (LlmConversationMessage message : messages)
        {
            require(message.getRole() != MessageRole.MESSAGE_ROLE_UNSPECIFIED,
                "Every request message requires a role.");
            require(message.getContentPartsCount() == 0,
                "Multimodal LLM request messages are not supported yet.");
            if (message.getRole() == MessageRole.MESSAGE_ROLE_ASSISTANT)
            {
                for (ToolCall toolCall : message.getToolCallsList())
                {
                    validateToolCall(toolCall);
                    require(priorToolCallIds.add(toolCall.getId()),
                        "Historical Tool call IDs must be unique in request order.");
                }
                require(StringUtils.hasText(message.getContent()) || message.getToolCallsCount() > 0,
                    "Assistant messages require text or Tool calls.");
                require(message.getToolCallId().isEmpty(),
                    "Assistant messages cannot contain tool_call_id.");
            }
            else if (message.getRole() == MessageRole.MESSAGE_ROLE_TOOL)
            {
                require(message.getToolCallsCount() == 0 && StringUtils.hasText(message.getToolCallId()),
                    "Tool messages require one prior tool_call_id and cannot emit Tool calls.");
                require(priorToolCallIds.contains(message.getToolCallId()),
                    "Tool messages must reference an earlier assistant Tool call.");
                require(StringUtils.hasText(message.getContent()), "Tool messages require result content.");
            }
            else
            {
                require(message.getToolCallsCount() == 0 && message.getToolCallId().isEmpty(),
                    "Only assistant and Tool messages may contain Tool metadata.");
                require(StringUtils.hasText(message.getContent()), "Request messages require text content.");
            }
        }
    }

    private void validateResponseAndExecutions(LlmResponse response, ConversationTurn turn,
                                                Map<String, ToolDefinition> toolsByName, long callEndTime)
    {
        require(response.hasMessage(), "A successful LLM response requires a message.");
        require(response.getMessage().getContentPartsCount() == 0,
            "Multimodal LLM responses are not supported yet.");
        require(StringUtils.hasText(response.getMessage().getContent())
                || response.getMessage().getToolCallsCount() > 0,
            "An LLM response requires text or Tool calls.");
        require(StringUtils.hasText(response.getFinishReason()) && response.getErrorMessage().isEmpty(),
            "A successful LLM response requires a finish reason and no error.");

        Map<String, ToolCall> callsById = new HashMap<>();
        for (ToolCall toolCall : response.getMessage().getToolCallsList())
        {
            validateToolCall(toolCall);
            require(callsById.put(toolCall.getId(), toolCall) == null,
                "Response Tool call IDs must be unique.");
            require(toolsByName.containsKey(toolCall.getFunction().getName()),
                "Every response Tool call must reference a frozen Tool definition.");
        }
        require(turn.getToolCallExecutionsCount() == callsById.size(),
            "Every response Tool call requires exactly one execution record.");

        Set<String> executedIds = new HashSet<>();
        for (ToolCallExecution execution : turn.getToolCallExecutionsList())
        {
            ToolCall toolCall = callsById.get(execution.getToolCallId());
            require(toolCall != null && executedIds.add(execution.getToolCallId()),
                "Tool executions must map one-to-one to response Tool calls.");
            ToolDefinition definition = toolsByName.get(toolCall.getFunction().getName());
            require(execution.getToolName().equals(toolCall.getFunction().getName())
                    && execution.getToolKey().equals(definition.getToolKey()),
                "Tool execution identity must match the frozen Tool mapping.");
            require(jsonEquals(execution.getArguments(), toolCall.getFunction().getArguments()),
                "Tool execution arguments must match the model-emitted arguments.");
            require(execution.getStatus() != ToolCallExecutionStatus.TOOL_CALL_EXECUTION_STATUS_UNSPECIFIED,
                "Tool execution status is required.");
            if (execution.getStatus() == ToolCallExecutionStatus.TOOL_CALL_EXECUTION_STATUS_COMPLETED)
            {
                require(execution.getErrorMessage().isEmpty(), "Completed Tool execution cannot contain an error.");
                require(StringUtils.hasText(execution.getResultContent()) && execution.hasRawResult(),
                    "Completed Tool execution requires complete normalized and raw results.");
            }
            if (execution.getStatus() == ToolCallExecutionStatus.TOOL_CALL_EXECUTION_STATUS_FAILED)
                require(StringUtils.hasText(execution.getErrorMessage())
                        && StringUtils.hasText(execution.getResultContent()) && execution.hasRawResult(),
                    "Failed Tool execution requires an error plus complete normalized and raw results.");
            if (execution.getStatus() == ToolCallExecutionStatus.TOOL_CALL_EXECUTION_STATUS_CANCELLED)
                require(StringUtils.hasText(execution.getErrorMessage()),
                    "Cancelled Tool execution requires a cancellation reason.");
            require(execution.getResultContentPartsCount() == 0,
                "Multimodal Tool results are not supported yet.");
            requireTime(execution.getStartTime(), execution.getEndTime(), "Tool execution");
            require(execution.getStartTime() >= callEndTime && execution.getEndTime() <= turn.getEndTime(),
                "Tool execution timing must follow the LLM call and remain within the Turn.");
        }

        if (response.hasUsage())
        {
            TokenUsage usage = response.getUsage();
            require(usage.getPromptTokens() >= 0 && usage.getCompletionTokens() >= 0
                    && usage.getTotalTokens() >= 0 && usage.getCachedPromptTokens() >= 0
                    && usage.getReasoningTokens() >= 0,
                "Token usage cannot be negative.");
        }
    }

    private void validateToolCall(ToolCall toolCall)
    {
        require(StringUtils.hasText(toolCall.getId()) && StringUtils.hasText(toolCall.getType())
                && toolCall.hasFunction() && StringUtils.hasText(toolCall.getFunction().getName())
                && StringUtils.hasText(toolCall.getFunction().getArguments())
                && isJson(toolCall.getFunction().getArguments()),
            "Tool calls require an ID, type, function name, and JSON arguments.");
    }

    private boolean jsonEquals(String first, String second)
    {
        try
        {
            JsonNode firstNode = objectMapper.readTree(first);
            JsonNode secondNode = objectMapper.readTree(second);
            return firstNode.equals(secondNode);
        }
        catch (JsonProcessingException e)
        {
            return false;
        }
    }

    private boolean isJson(String value)
    {
        try
        {
            return objectMapper.readTree(value) != null;
        }
        catch (JsonProcessingException e)
        {
            return false;
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
