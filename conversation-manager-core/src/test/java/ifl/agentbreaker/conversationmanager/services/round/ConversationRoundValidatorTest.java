package ifl.agentbreaker.conversationmanager.services.round;

import ifl.agentbreaker.commons.api.dto.AgentIdentity;
import ifl.agentbreaker.conversationmanager.rpc.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConversationRoundValidatorTest
{
    private final ConversationRoundValidator validator = new ConversationRoundValidator();
    private final ConversationRoundPayloadHasher hasher = new ConversationRoundPayloadHasher();

    @Test
    void acceptsOneCompletedTextTurn()
    {
        assertDoesNotThrow(() -> validator.validatePhaseThree(validRequest(1, "answer")));
    }

    @Test
    void rejectsASecondTurn()
    {
        SaveConversationRoundRequest request = validRequest(1, "answer").toBuilder()
            .addTurns(validRequest(1, "answer").getTurns(0).toBuilder().setTurnNumber(2))
            .build();

        RoundPersistenceException error = assertThrows(
            RoundPersistenceException.class, () -> validator.validatePhaseThree(request));
        assertEquals(ConversationErrorCode.CONVERSATION_ERROR_CODE_INVALID_REQUEST_VALUE, error.getCode());
    }

    @Test
    void canonicalHashExcludesCallerIdentityButIncludesPersistedContent()
    {
        String first = hasher.hash(validRequest(1, "answer"));
        String anotherUser = hasher.hash(validRequest(2, "answer"));
        String changedAnswer = hasher.hash(validRequest(1, "changed"));

        assertEquals(first, anotherUser);
        assertNotEquals(first, changedAnswer);
        assertEquals(64, first.length());
    }

    private SaveConversationRoundRequest validRequest(long userId, String answer)
    {
        long start = 1_700_000_000_000L;
        LlmRequest llmRequest = LlmRequest.newBuilder()
            .setProvider("litellm")
            .setModel("test-model")
            .setMessageStorageMode(LlmMessageStorageMode.LLM_MESSAGE_STORAGE_MODE_FULL_SNAPSHOT)
            .addMessages(LlmConversationMessage.newBuilder()
                .setRole(MessageRole.MESSAGE_ROLE_SYSTEM).setContent("system"))
            .addMessages(LlmConversationMessage.newBuilder()
                .setRole(MessageRole.MESSAGE_ROLE_USER).setContent("question"))
            .build();
        LlmResponse llmResponse = LlmResponse.newBuilder()
            .setMessage(AssistantMessage.newBuilder().setContent(answer))
            .setFinishReason("stop")
            .setUsage(TokenUsage.newBuilder().setPromptTokens(2).setCompletionTokens(1).setTotalTokens(3))
            .build();
        LlmCall call = LlmCall.newBuilder()
            .setRequest(llmRequest).setResponse(llmResponse)
            .setRequestId("request").setTraceId("trace")
            .setStartTime(start).setEndTime(start + 10)
            .build();
        ifl.agentbreaker.conversationmanager.rpc.ConversationTurn turn =
            ifl.agentbreaker.conversationmanager.rpc.ConversationTurn.newBuilder()
                .setTurnNumber(1).setLlmCall(call)
                .setStatus(TurnStatus.TURN_STATUS_COMPLETED)
                .setStartTime(start).setEndTime(start + 10)
                .setAgentIdentity(AgentIdentity.newBuilder()
                    .setAgentId(1).setName("general-assistant").setVersion(1))
                .build();
        return SaveConversationRoundRequest.newBuilder()
            .setUserId(userId)
            .setConversationId("conv_test")
            .setRoundNumber(1)
            .setUserRequest(UserRequest.newBuilder().setContent("question"))
            .addTurns(turn)
            .setFinalAnswer(AssistantAnswer.newBuilder().setContent(answer).setSourceTurnNumber(1))
            .setStatus(RoundStatus.ROUND_STATUS_COMPLETED)
            .setStartTime(start).setEndTime(start + 10)
            .build();
    }
}
