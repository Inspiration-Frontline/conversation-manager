package ifl.agentbreaker.conversationmanager.services.rounds;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ifl.agentbreaker.commons.api.dto.AgentIdentity;
import ifl.agentbreaker.conversationmanager.rpc.AssistantMessage;
import ifl.agentbreaker.conversationmanager.rpc.ConversationErrorCode;
import ifl.agentbreaker.conversationmanager.rpc.ConversationTurn;
import ifl.agentbreaker.conversationmanager.rpc.ContentPart;
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

/**
 * Guards the cross-service Round/Turn contract before any SQL mutation. Runner captures data from a
 * third-party SDK, so Conversation Manager revalidates ordering, terminal states, Tool evidence,
 * content shape, and timing instead of trusting a well-formed protobuf message.
 */
@Component
public class ConversationRoundValidator
{
    @Autowired
    private ObjectMapper objectMapper;

    // TODO: Rename to validate() once the remaining Round/Turn phases share this contract.
    /**
     * Validates the complete Round persistence contract before any database mutation occurs.
     * Completed and partial terminal Rounds intentionally have different answer/Turn requirements.
     *
     * @param request complete Runner persistence request
     * @throws RoundPersistenceException when any cross-row invariant is invalid
     */
    public void validatePhaseFour(SaveConversationRoundRequest request)
    {
        require(request.getUserId() > 0, "user_id must be positive.");
        require(StringUtils.hasText(request.getConversationId()), "conversation_id is required.");
        require(request.getRoundNumber() > 0, "round_number must be positive.");
        require(request.hasUserRequest(), "A user request is required.");
        validateContentPayload(
            request.getUserRequest().getContent(),
            request.getUserRequest().getContentPartsList(),
            "user request");
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

    /**
     * Validates Turn ordering, fixed Agent identity, nested timing, and terminal-state continuity.
     *
     * @param request parent Round containing ordered Turns
     * @param completedRound whether every Turn must be successful
     */
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

    /**
     * Ensures an APPEND_DELTA exactly continues the preceding Tool-calling Turn so replay cannot
     * lose, duplicate, or rewrite a Tool result between model calls.
     *
     * @param previousTurn preceding model response and Tool executions
     * @param currentRequest next LLM request using APPEND_DELTA storage
     */
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

    /**
     * Compares Tool calls structurally, parsing JSON arguments rather than treating formatting as a
     * semantic difference.
     *
     * @param first first Tool call
     * @param second second Tool call
     * @return true when identity, function, and JSON arguments match
     */
    private boolean toolCallEquals(ToolCall first, ToolCall second)
    {
        return first.getId().equals(second.getId())
            && first.getType().equals(second.getType())
            && first.getFunction().getName().equals(second.getFunction().getName())
            && jsonEquals(first.getFunction().getArguments(), second.getFunction().getArguments());
    }

    /**
     * Validates one nested LLM call including storage mode, provider metadata, response, and Tool
     * execution evidence.
     *
     * @param call nested model call
     * @param turn parent Turn defining the time boundary
     * @param turnIndex zero-based Turn index selecting FULL_SNAPSHOT versus APPEND_DELTA
     */
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

    /**
     * Validates the exact frozen Tool schemas offered to the model and indexes them for response
     * verification.
     *
     * @param request model request containing Tool definitions
     * @return definitions indexed by provider-visible Tool name
     */
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

    /**
     * Validates normalized message roles, scalar/parts content, and Tool-call ordering.
     *
     * @param messages request messages in model order
     */
    private void validateRequestMessages(Iterable<LlmConversationMessage> messages)
    {
        Set<String> priorToolCallIds = new HashSet<>();
        for (LlmConversationMessage message : messages)
        {
            require(message.getRole() != MessageRole.MESSAGE_ROLE_UNSPECIFIED,
                "Every request message requires a role.");
            if (message.getRole() == MessageRole.MESSAGE_ROLE_ASSISTANT)
            {
                for (ToolCall toolCall : message.getToolCallsList())
                {
                    validateToolCall(toolCall);
                    require(priorToolCallIds.add(toolCall.getId()),
                        "Historical Tool call IDs must be unique in request order.");
                }
                require(StringUtils.hasText(message.getContent())
                        || message.getContentPartsCount() > 0
                        || message.getToolCallsCount() > 0,
                    "Assistant messages require content or Tool calls.");
                if (message.getContentPartsCount() > 0)
                    validateContentPayload(message.getContent(), message.getContentPartsList(), "Assistant message");
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
                require(message.getContentPartsCount() == 0, "Tool messages do not support content parts yet.");
            }
            else
            {
                require(message.getToolCallsCount() == 0 && message.getToolCallId().isEmpty(),
                    "Only assistant and Tool messages may contain Tool metadata.");
                validateContentPayload(message.getContent(), message.getContentPartsList(), "request message");
            }
        }
    }

    /**
     * Validates the model response and enforces one terminal execution record per emitted Tool call.
     *
     * @param response normalized model response
     * @param turn parent Turn containing execution evidence
     * @param toolsByName frozen definitions indexed by model-visible name
     * @param callEndTime model-call end used to validate execution timing
     */
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

    /**
     * Validates Tool call identity, function metadata, and JSON arguments.
     *
     * @param toolCall model-emitted or replayed Tool call
     */
    private void validateToolCall(ToolCall toolCall)
    {
        require(StringUtils.hasText(toolCall.getId()) && StringUtils.hasText(toolCall.getType())
                && toolCall.hasFunction() && StringUtils.hasText(toolCall.getFunction().getName())
                && StringUtils.hasText(toolCall.getFunction().getArguments())
                && isJson(toolCall.getFunction().getArguments()),
            "Tool calls require an ID, type, function name, and JSON arguments.");
    }

    /**
     * Enforces the mutually exclusive scalar-text or structured-content-parts contract. This is the
     * invariant that prevents an adapter from persisting two contradictory representations of one
     * message.
     *
     * @param content scalar text representation
     * @param contentParts structured representation
     * @param label message label included in validation errors
     */
    private void validateContentPayload(String content, List<ContentPart> contentParts, String label)
    {
        boolean hasContent = StringUtils.hasText(content);
        boolean hasContentParts = contentParts != null && !contentParts.isEmpty();
        require(hasContent != hasContentParts, label + " must contain text or content parts, but not both.");
        if (!hasContentParts)
            return;

        for (ContentPart contentPart : contentParts)
        {
            require(contentPart.getType().equals("text")
                    || contentPart.getType().equals("image_url")
                    || contentPart.getType().equals("file_url"),
                label + " contains an unsupported content part type.");
            if (contentPart.getType().equals("text"))
            {
                require(StringUtils.hasText(contentPart.getText()) && !contentPart.hasFileUrl(),
                    label + " text parts require text only.");
            }
            else
            {
                require(contentPart.getText().isEmpty()
                        && contentPart.hasFileUrl()
                        && StringUtils.hasText(contentPart.getFileUrl().getUrl()),
                    label + " file parts require a file URL only.");
            }
        }
    }

    /**
     * Compares two JSON values structurally so formatting differences do not matter.
     *
     * @param first first JSON string
     * @param second second JSON string
     * @return true when both parse and contain the same JSON value
     */
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

    /**
     * Checks whether a string parses as one non-null JSON value.
     *
     * @param value candidate JSON
     * @return true when parsing succeeds
     */
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

    /**
     * Rejects missing or backwards timestamps with a field-specific validation message.
     *
     * @param start inclusive start epoch milliseconds
     * @param end inclusive end epoch milliseconds
     * @param label timing owner included in the error
     */
    private void requireTime(long start, long end, String label)
    {
        require(start > 0 && end >= start, label + " timing is invalid.");
    }

    /**
     * Throws the standard protocol invalid-request exception when a condition is false.
     *
     * @param condition invariant result
     * @param message client-safe validation explanation
     * @throws RoundPersistenceException when {@code condition} is false
     */
    private void require(boolean condition, String message)
    {
        if (!condition)
            throw new RoundPersistenceException(
                ConversationErrorCode.CONVERSATION_ERROR_CODE_INVALID_REQUEST_VALUE, message);
    }
}
