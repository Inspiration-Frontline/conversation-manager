package ifl.agentbreaker.conversationmanager.services.round;

import ifl.agentbreaker.conversationmanager.dao.*;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.ConversationReplayResult;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.ConversationRoundHistoryResult;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.RoundHistoryView;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationLlmCall;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationLlmRequestMessage;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationRound;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationTurn;
import ifl.agentbreaker.conversationmanager.rpc.LlmConversationMessage;
import ifl.agentbreaker.conversationmanager.rpc.MessageRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import stark.dataworks.boot.web.ServiceResponse;

import java.util.List;

@Service
public class ConversationRoundQueryService
{
    private static final int ERROR_CONVERSATION_NOT_FOUND = 2002;

    @Autowired
    private ConversationMapper conversationMapper;

    @Autowired
    private ConversationRoundMapper conversationRoundMapper;

    @Autowired
    private ConversationTurnMapper conversationTurnMapper;

    @Autowired
    private ConversationLlmCallMapper conversationLlmCallMapper;

    @Autowired
    private ConversationLlmRequestMessageMapper conversationLlmRequestMessageMapper;

    public ConversationRoundHistoryResult getHistory(long userId, String conversationId)
    {
        Long latestRoundNumber = conversationMapper.getLatestRoundNumberByIdAndUser(conversationId, userId);
        if (latestRoundNumber == null)
            throw new RoundPersistenceException(ERROR_CONVERSATION_NOT_FOUND, "Conversation does not exist.");
        return new ConversationRoundHistoryResult(
            latestRoundNumber, conversationRoundMapper.listActiveRounds(conversationId));
    }

    public ServiceResponse<RoundHistoryView> getHttpHistory(long userId, String conversationId)
    {
        try
        {
            ConversationRoundHistoryResult conversationRoundHistoryResult = getHistory(userId, conversationId);
            List<RoundHistoryView.RoundView> rounds = conversationRoundHistoryResult.rounds().stream()
                .map(round -> new RoundHistoryView.RoundView(
                    round.getRoundNumber(), round.getUserRequestContent(), round.getFinalAnswerContent(),
                    round.getStatus().name(), round.getErrorMessage(),
                    conversationRoundMapper.countTurns(round.getId()),
                    round.getStartTime().getTime(), round.getEndTime().getTime()))
                .toList();
            return ServiceResponse.buildSuccessResponse(
                new RoundHistoryView(conversationId, conversationRoundHistoryResult.latestRoundNumber(), rounds));
        }
        catch (RoundPersistenceException e)
        {
            return ServiceResponse.buildErrorResponse(e.getCode(), e.getMessage());
        }
    }

    public long getTurnCount(long roundId)
    {
        return conversationRoundMapper.countTurns(roundId);
    }

    public ConversationReplayResult getModelContext(long userId, String conversationId, long endRoundNumber)
    {
        Long latestRoundNumber = conversationMapper.getLatestRoundNumberByIdAndUser(conversationId, userId);
        if (latestRoundNumber == null)
            throw new RoundPersistenceException(ERROR_CONVERSATION_NOT_FOUND, "Conversation does not exist.");
        if (endRoundNumber <= 0 || endRoundNumber > latestRoundNumber)
            throw new RoundPersistenceException(
                ifl.agentbreaker.conversationmanager.rpc.ConversationErrorCode
                    .CONVERSATION_ERROR_CODE_INVALID_REQUEST_VALUE,
                "end_round_number must reference an assigned round.");

        ConversationRound boundaryRound = conversationRoundMapper.getRound(conversationId, endRoundNumber);
        if (boundaryRound == null || boundaryRound.isDeleted())
            throw new RoundPersistenceException(
                ifl.agentbreaker.conversationmanager.rpc.ConversationErrorCode
                    .CONVERSATION_ERROR_CODE_ROUND_NOT_FOUND_VALUE,
                "Replay boundary round does not exist.");

        ConversationRound completedRound = conversationRoundMapper.getLatestCompletedRoundAtOrBefore(
            conversationId, endRoundNumber);
        if (completedRound == null)
            return new ConversationReplayResult(conversationId, List.of());

        ConversationTurn conversationTurn = conversationTurnMapper.getCompletedTurn(
            completedRound.getId(), completedRound.getFinalSourceTurnNumber());
        if (conversationTurn == null)
            throw new IllegalStateException("Completed replay round has no source turn.");
        ConversationLlmCall conversationLlmCall = conversationLlmCallMapper.getLlmCallByTurnId(
            conversationTurn.getId());
        if (conversationLlmCall == null || !conversationLlmCall.isResponseMessagePresent())
            throw new IllegalStateException("Completed replay turn has no LLM response.");

        List<LlmConversationMessage> contextMessages = conversationLlmRequestMessageMapper
            .listRequestMessages(conversationLlmCall.getId()).stream()
            .map(this::toProtoMessage)
            .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        contextMessages.add(LlmConversationMessage.newBuilder()
            .setRole(MessageRole.MESSAGE_ROLE_ASSISTANT)
            .setContent(conversationLlmCall.getResponseContent())
            .build());
        return new ConversationReplayResult(conversationId, List.copyOf(contextMessages));
    }

    private LlmConversationMessage toProtoMessage(ConversationLlmRequestMessage message)
    {
        return LlmConversationMessage.newBuilder()
            .setRole(switch (message.getRole())
            {
                case SYSTEM -> MessageRole.MESSAGE_ROLE_SYSTEM;
                case USER -> MessageRole.MESSAGE_ROLE_USER;
                case ASSISTANT -> MessageRole.MESSAGE_ROLE_ASSISTANT;
                case TOOL -> MessageRole.MESSAGE_ROLE_TOOL;
                case DEVELOPER -> MessageRole.MESSAGE_ROLE_DEVELOPER;
            })
            .setContent(message.getContent())
            .setToolCallId(message.getToolCallId() == null ? "" : message.getToolCallId())
            .build();
    }
}
