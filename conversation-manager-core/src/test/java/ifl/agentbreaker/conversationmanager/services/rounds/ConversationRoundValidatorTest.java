package ifl.agentbreaker.conversationmanager.services.rounds;

import com.fasterxml.jackson.databind.ObjectMapper;
import ifl.agentbreaker.commons.api.dto.AgentIdentity;
import ifl.agentbreaker.conversationmanager.rpc.AssistantAnswer;
import ifl.agentbreaker.conversationmanager.rpc.AssistantMessage;
import ifl.agentbreaker.conversationmanager.rpc.ConversationErrorCode;
import ifl.agentbreaker.conversationmanager.rpc.ConversationTurn;
import ifl.agentbreaker.conversationmanager.rpc.FunctionCall;
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
import ifl.agentbreaker.conversationmanager.rpc.UserRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConversationRoundValidatorTest
{
    private static final long START = 1_700_000_000_000L;
    private static final String DEFINITION_HASH = "0".repeat(64);

    private final ConversationRoundValidator validator = new ConversationRoundValidator();
    private final ConversationRoundPayloadHasher hasher = new ConversationRoundPayloadHasher();

    @BeforeEach
    void setUp()
    {
        ReflectionTestUtils.setField(validator, "objectMapper", new ObjectMapper());
    }

    @Test
    void acceptsOneCompletedTextTurn()
    {
        assertDoesNotThrow(() -> validator.validatePhaseFour(validTextRequest(1, "answer")));
    }

    @Test
    void acceptsTwoTurnLoopWithParallelCallsAndPartialToolFailure()
    {
        assertDoesNotThrow(() -> validator.validatePhaseFour(validToolLoopRequest()));
    }

    @Test
    void rejectsContinuationWhoseToolResultDoesNotMatchThePreviousExecution()
    {
        SaveConversationRoundRequest valid = validToolLoopRequest();
        ConversationTurn invalidSecondTurn = valid.getTurns(1).toBuilder()
            .setLlmCall(valid.getTurns(1).getLlmCall().toBuilder()
                .setRequest(valid.getTurns(1).getLlmCall().getRequest().toBuilder()
                    .setMessages(1, valid.getTurns(1).getLlmCall().getRequest().getMessages(1).toBuilder()
                        .setContent("wrong result"))))
            .build();
        SaveConversationRoundRequest invalid = valid.toBuilder().setTurns(1, invalidSecondTurn).build();

        RoundPersistenceException error = assertThrows(
            RoundPersistenceException.class, () -> validator.validatePhaseFour(invalid));

        assertEquals(ConversationErrorCode.CONVERSATION_ERROR_CODE_INVALID_REQUEST_VALUE, error.getCode());
    }

    @Test
    void rejectsToolCallWithoutOneExecutionOutcome()
    {
        SaveConversationRoundRequest valid = validToolLoopRequest();
        ConversationTurn invalidFirstTurn = valid.getTurns(0).toBuilder()
            .removeToolCallExecutions(1)
            .build();
        SaveConversationRoundRequest invalid = valid.toBuilder().setTurns(0, invalidFirstTurn).build();

        assertThrows(RoundPersistenceException.class, () -> validator.validatePhaseFour(invalid));
    }

    @Test
    void acceptsCancelledRoundWithCancelledInFlightTool()
    {
        SaveConversationRoundRequest valid = validToolLoopRequest();
        ConversationTurn firstTurn = valid.getTurns(0);
        ToolCallExecution cancelledExecution = firstTurn.getToolCallExecutions(0).toBuilder()
            .setStatus(ToolCallExecutionStatus.TOOL_CALL_EXECUTION_STATUS_CANCELLED)
            .clearResultContent()
            .clearRawResult()
            .setErrorMessage("Generation cancelled.")
            .build();
        ConversationTurn cancelledTurn = firstTurn.toBuilder()
            .clearToolCallExecutions()
            .addToolCallExecutions(cancelledExecution)
            .addToolCallExecutions(firstTurn.getToolCallExecutions(1).toBuilder()
                .setStatus(ToolCallExecutionStatus.TOOL_CALL_EXECUTION_STATUS_CANCELLED)
                .clearResultContent()
                .clearRawResult()
                .setErrorMessage("Generation cancelled."))
            .setStatus(TurnStatus.TURN_STATUS_CANCELLED)
            .setErrorMessage("Generation cancelled.")
            .build();
        SaveConversationRoundRequest cancelled = valid.toBuilder()
            .clearTurns()
            .addTurns(cancelledTurn)
            .clearFinalAnswer()
            .setStatus(RoundStatus.ROUND_STATUS_CANCELLED)
            .setErrorMessage("Generation cancelled.")
            .setEndTime(START + 25)
            .build();

        assertDoesNotThrow(() -> validator.validatePhaseFour(cancelled));
    }

    @Test
    void acceptsFailedAndCancelledRoundsBeforeAnyModelTurn()
    {
        SaveConversationRoundRequest base = validTextRequest(1, "answer");
        SaveConversationRoundRequest failed = base.toBuilder()
            .clearTurns().clearFinalAnswer()
            .setStatus(RoundStatus.ROUND_STATUS_FAILED)
            .setErrorMessage("provider unavailable")
            .build();
        SaveConversationRoundRequest cancelled = base.toBuilder()
            .clearTurns().clearFinalAnswer()
            .setStatus(RoundStatus.ROUND_STATUS_CANCELLED)
            .setErrorMessage("Generation cancelled.")
            .build();

        assertDoesNotThrow(() -> validator.validatePhaseFour(failed));
        assertDoesNotThrow(() -> validator.validatePhaseFour(cancelled));
    }

    @Test
    void canonicalHashExcludesCallerIdentityButIncludesPersistedContent()
    {
        String first = hasher.hash(validTextRequest(1, "answer"));
        String anotherUser = hasher.hash(validTextRequest(2, "answer"));
        String changedAnswer = hasher.hash(validTextRequest(1, "changed"));

        assertEquals(first, anotherUser);
        assertNotEquals(first, changedAnswer);
        assertEquals(64, first.length());
    }

    private SaveConversationRoundRequest validToolLoopRequest()
    {
        ToolCall timeCall = toolCall("call-time", "get_current_time", "{\"timezone\":\"Asia/Shanghai\"}");
        ToolCall calculatorCall = toolCall("call-calc", "calculate_expression", "{\"expression\":\"1/0\"}");
        String timeResult = "{\"timezone\":\"Asia/Shanghai\",\"utc_offset\":\"+08:00\"}";
        String calculatorResult = "{\"status\":\"error\",\"error\":\"division by zero\"}";

        LlmRequest firstRequest = LlmRequest.newBuilder()
            .setProvider("litellm")
            .setModel("test-model")
            .setMessageStorageMode(LlmMessageStorageMode.LLM_MESSAGE_STORAGE_MODE_FULL_SNAPSHOT)
            .addMessages(message(MessageRole.MESSAGE_ROLE_SYSTEM, "system"))
            .addMessages(message(MessageRole.MESSAGE_ROLE_USER, "question"))
            .addTools(toolDefinition("builtin.current_time", "get_current_time"))
            .addTools(toolDefinition("builtin.calculator", "calculate_expression"))
            .build();
        LlmResponse firstResponse = response("", "tool_calls", timeCall, calculatorCall);
        ConversationTurn firstTurn = turnBuilder(1, START, START + 25)
            .setLlmCall(call(firstRequest, firstResponse, START, START + 10, "request-1"))
            .addToolCallExecutions(execution(
                timeCall, "builtin.current_time", ToolCallExecutionStatus.TOOL_CALL_EXECUTION_STATUS_COMPLETED,
                timeResult, "", START + 10, START + 20))
            .addToolCallExecutions(execution(
                calculatorCall, "builtin.calculator", ToolCallExecutionStatus.TOOL_CALL_EXECUTION_STATUS_FAILED,
                calculatorResult, "division by zero", START + 10, START + 25))
            .build();

        LlmConversationMessage assistantDelta = LlmConversationMessage.newBuilder()
            .setRole(MessageRole.MESSAGE_ROLE_ASSISTANT)
            .addToolCalls(timeCall)
            .addToolCalls(calculatorCall)
            .build();
        LlmRequest secondRequest = LlmRequest.newBuilder()
            .setProvider("litellm")
            .setModel("test-model")
            .setMessageStorageMode(LlmMessageStorageMode.LLM_MESSAGE_STORAGE_MODE_APPEND_DELTA)
            .addMessages(assistantDelta)
            .addMessages(toolMessage("call-time", timeResult))
            .addMessages(toolMessage("call-calc", calculatorResult))
            .addTools(toolDefinition("builtin.current_time", "get_current_time"))
            .addTools(toolDefinition("builtin.calculator", "calculate_expression"))
            .build();
        ConversationTurn secondTurn = turnBuilder(2, START + 25, START + 35)
            .setLlmCall(call(secondRequest, response("final answer", "stop"),
                START + 25, START + 35, "request-2"))
            .build();

        return SaveConversationRoundRequest.newBuilder()
            .setUserId(1)
            .setConversationId("conv_tool_loop")
            .setRoundNumber(1)
            .setUserRequest(UserRequest.newBuilder().setContent("question"))
            .addTurns(firstTurn)
            .addTurns(secondTurn)
            .setFinalAnswer(AssistantAnswer.newBuilder().setContent("final answer").setSourceTurnNumber(2))
            .setStatus(RoundStatus.ROUND_STATUS_COMPLETED)
            .setStartTime(START)
            .setEndTime(START + 35)
            .build();
    }

    private SaveConversationRoundRequest validTextRequest(long userId, String answer)
    {
        LlmRequest request = LlmRequest.newBuilder()
            .setProvider("litellm")
            .setModel("test-model")
            .setMessageStorageMode(LlmMessageStorageMode.LLM_MESSAGE_STORAGE_MODE_FULL_SNAPSHOT)
            .addMessages(message(MessageRole.MESSAGE_ROLE_SYSTEM, "system"))
            .addMessages(message(MessageRole.MESSAGE_ROLE_USER, "question"))
            .build();
        ConversationTurn turn = turnBuilder(1, START, START + 10)
            .setLlmCall(call(request, response(answer, "stop"), START, START + 10, "request"))
            .build();
        return SaveConversationRoundRequest.newBuilder()
            .setUserId(userId)
            .setConversationId("conv_test")
            .setRoundNumber(1)
            .setUserRequest(UserRequest.newBuilder().setContent("question"))
            .addTurns(turn)
            .setFinalAnswer(AssistantAnswer.newBuilder().setContent(answer).setSourceTurnNumber(1))
            .setStatus(RoundStatus.ROUND_STATUS_COMPLETED)
            .setStartTime(START)
            .setEndTime(START + 10)
            .build();
    }

    private ConversationTurn.Builder turnBuilder(int number, long start, long end)
    {
        return ConversationTurn.newBuilder()
            .setTurnNumber(number)
            .setStatus(TurnStatus.TURN_STATUS_COMPLETED)
            .setStartTime(start)
            .setEndTime(end)
            .setAgentIdentity(AgentIdentity.newBuilder()
                .setAgentId(1).setName("general-assistant").setVersion(1));
    }

    private LlmCall call(LlmRequest request, LlmResponse response, long start, long end, String requestId)
    {
        return LlmCall.newBuilder()
            .setRequest(request)
            .setResponse(response)
            .setRequestId(requestId)
            .setTraceId("trace")
            .setStartTime(start)
            .setEndTime(end)
            .build();
    }

    private LlmResponse response(String content, String finishReason, ToolCall... calls)
    {
        AssistantMessage.Builder message = AssistantMessage.newBuilder().setContent(content);
        for (ToolCall call : calls)
            message.addToolCalls(call);
        return LlmResponse.newBuilder()
            .setMessage(message)
            .setFinishReason(finishReason)
            .setUsage(TokenUsage.newBuilder().setPromptTokens(2).setCompletionTokens(1).setTotalTokens(3))
            .build();
    }

    private ToolDefinition toolDefinition(String key, String name)
    {
        return ToolDefinition.newBuilder()
            .setSourceType(ToolSourceType.TOOL_SOURCE_TYPE_INTERNAL)
            .setToolKey(key)
            .setToolName(name)
            .setDescription("test")
            .setParametersJson("{\"type\":\"object\"}")
            .setStrict(true)
            .setDefinitionHash(DEFINITION_HASH)
            .build();
    }

    private ToolCall toolCall(String id, String name, String arguments)
    {
        return ToolCall.newBuilder()
            .setId(id)
            .setType("function")
            .setFunction(FunctionCall.newBuilder().setName(name).setArguments(arguments))
            .build();
    }

    private ToolCallExecution execution(ToolCall call, String key, ToolCallExecutionStatus status,
                                        String result, String error, long start, long end)
    {
        return ToolCallExecution.newBuilder()
            .setToolCallId(call.getId())
            .setToolKey(key)
            .setToolName(call.getFunction().getName())
            .setArguments(call.getFunction().getArguments())
            .setStatus(status)
            .setResultContent(result)
            .setRawResult(result)
            .setErrorMessage(error)
            .setStartTime(start)
            .setEndTime(end)
            .build();
    }

    private LlmConversationMessage message(MessageRole role, String content)
    {
        return LlmConversationMessage.newBuilder().setRole(role).setContent(content).build();
    }

    private LlmConversationMessage toolMessage(String callId, String content)
    {
        return LlmConversationMessage.newBuilder()
            .setRole(MessageRole.MESSAGE_ROLE_TOOL)
            .setToolCallId(callId)
            .setContent(content)
            .build();
    }
}
