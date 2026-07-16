package ifl.agentbreaker.conversationmanager.services.round;

import ifl.agentbreaker.commons.api.dto.ResponseBase;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.ConversationRoundHistoryResult;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.ConversationReplayResult;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationRound;
import ifl.agentbreaker.conversationmanager.rpc.AssistantAnswer;
import ifl.agentbreaker.conversationmanager.rpc.ConversationAbstract;
import ifl.agentbreaker.conversationmanager.rpc.ConversationErrorCode;
import ifl.agentbreaker.conversationmanager.rpc.ConversationReplay;
import ifl.agentbreaker.conversationmanager.rpc.ConversationRoundHistory;
import ifl.agentbreaker.conversationmanager.rpc.ConversationRoundSummary;
import ifl.agentbreaker.conversationmanager.rpc.ConversationRpcService;
import ifl.agentbreaker.conversationmanager.rpc.ConversationTurnHistory;
import ifl.agentbreaker.conversationmanager.rpc.CreateConversationRequest;
import ifl.agentbreaker.conversationmanager.rpc.CreateConversationResponse;
import ifl.agentbreaker.conversationmanager.rpc.DeleteRoundsRequest;
import ifl.agentbreaker.conversationmanager.rpc.DeleteRoundsResponse;
import ifl.agentbreaker.conversationmanager.rpc.DeleteRoundsResult;
import ifl.agentbreaker.conversationmanager.rpc.GetConversationReplayRequest;
import ifl.agentbreaker.conversationmanager.rpc.GetConversationReplayResponse;
import ifl.agentbreaker.conversationmanager.rpc.GetConversationRoundHistoryRequest;
import ifl.agentbreaker.conversationmanager.rpc.GetConversationRoundHistoryResponse;
import ifl.agentbreaker.conversationmanager.rpc.GetConversationTurnHistoryRequest;
import ifl.agentbreaker.conversationmanager.rpc.GetConversationTurnHistoryResponse;
import ifl.agentbreaker.conversationmanager.rpc.ReplayDetailLevel;
import ifl.agentbreaker.conversationmanager.rpc.RoundStatus;
import ifl.agentbreaker.conversationmanager.rpc.SaveConversationRoundRequest;
import ifl.agentbreaker.conversationmanager.rpc.SaveConversationRoundResponse;
import ifl.agentbreaker.conversationmanager.rpc.UserRequest;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.CompletableFuture;

@DubboService
public class ConversationRoundRpcProvider implements ConversationRpcService
{
    @Autowired
    private ConversationRoundService conversationRoundService;

    @Override
    public CreateConversationResponse createConversation(CreateConversationRequest request)
    {
        return CreateConversationResponse.newBuilder()
            .setBase(errorBase(ConversationErrorCode.CONVERSATION_ERROR_CODE_INVALID_REQUEST_VALUE,
                "CreateConversation is not exposed by this Round persistence provider."))
            .setData(ConversationAbstract.getDefaultInstance())
            .build();
    }

    @Override
    public CompletableFuture<CreateConversationResponse> createConversationAsync(CreateConversationRequest request)
    {
        return CompletableFuture.completedFuture(createConversation(request));
    }

    @Override
    public SaveConversationRoundResponse saveConversationRound(SaveConversationRoundRequest request)
    {
        try
        {
            SaveConversationRoundRequest savedRequest = conversationRoundService.save(request);
            return SaveConversationRoundResponse.newBuilder()
                .setBase(successBase())
                .setData(toProtoRound(savedRequest))
                .build();
        }
        catch (RoundPersistenceException e)
        {
            return SaveConversationRoundResponse.newBuilder()
                .setBase(errorBase(e.getCode(), e.getMessage()))
                .setData(ifl.agentbreaker.conversationmanager.rpc.ConversationRound.getDefaultInstance())
                .build();
        }
    }

    @Override
    public CompletableFuture<SaveConversationRoundResponse> saveConversationRoundAsync(
        SaveConversationRoundRequest request)
    {
        return CompletableFuture.completedFuture(saveConversationRound(request));
    }

    @Override
    public GetConversationRoundHistoryResponse getConversationRoundHistory(GetConversationRoundHistoryRequest request)
    {
        try
        {
            ConversationRoundHistoryResult conversationRoundHistoryResult = conversationRoundService.getHistory(
                request.getUserId(), request.getConversationId());
            ConversationRoundHistory.Builder data = ConversationRoundHistory.newBuilder()
                .setConversationId(request.getConversationId())
                .setLatestRoundNumber(conversationRoundHistoryResult.latestRoundNumber());
            for (ConversationRound round : conversationRoundHistoryResult.rounds())
                data.addRounds(toSummary(round));
            return GetConversationRoundHistoryResponse.newBuilder().setBase(successBase()).setData(data).build();
        }
        catch (RoundPersistenceException e)
        {
            return GetConversationRoundHistoryResponse.newBuilder()
                .setBase(errorBase(e.getCode(), e.getMessage()))
                .setData(ConversationRoundHistory.newBuilder().setConversationId(request.getConversationId()))
                .build();
        }
    }

    @Override
    public CompletableFuture<GetConversationRoundHistoryResponse> getConversationRoundHistoryAsync(
        GetConversationRoundHistoryRequest request)
    {
        return CompletableFuture.completedFuture(getConversationRoundHistory(request));
    }

    @Override
    public GetConversationTurnHistoryResponse getConversationTurnHistory(GetConversationTurnHistoryRequest request)
    {
        return GetConversationTurnHistoryResponse.newBuilder()
            .setBase(errorBase(ConversationErrorCode.CONVERSATION_ERROR_CODE_INVALID_REQUEST_VALUE,
                "FULL_TURNS history is not implemented yet."))
            .setData(ConversationTurnHistory.getDefaultInstance())
            .build();
    }

    @Override
    public CompletableFuture<GetConversationTurnHistoryResponse> getConversationTurnHistoryAsync(
        GetConversationTurnHistoryRequest request)
    {
        return CompletableFuture.completedFuture(getConversationTurnHistory(request));
    }

    @Override
    public GetConversationReplayResponse getConversationReplay(GetConversationReplayRequest request)
    {
        try
        {
            if (request.getDetailLevel() != ReplayDetailLevel.REPLAY_DETAIL_LEVEL_MODEL_CONTEXT)
                throw new RoundPersistenceException(
                    ConversationErrorCode.CONVERSATION_ERROR_CODE_INVALID_REQUEST_VALUE,
                    "Only MODEL_CONTEXT replay is implemented.");
            ConversationReplayResult conversationReplayResult = conversationRoundService.getModelContext(
                request.getUserId(), request.getConversationId(), request.getEndRoundNumber());
            return GetConversationReplayResponse.newBuilder()
                .setBase(successBase())
                .setData(ConversationReplay.newBuilder()
                    .setConversationId(conversationReplayResult.conversationId())
                    .addAllContextMessages(conversationReplayResult.contextMessages()))
                .build();
        }
        catch (RoundPersistenceException e)
        {
            return GetConversationReplayResponse.newBuilder()
                .setBase(errorBase(e.getCode(), e.getMessage()))
                .setData(ConversationReplay.newBuilder().setConversationId(request.getConversationId()))
                .build();
        }
    }

    @Override
    public CompletableFuture<GetConversationReplayResponse> getConversationReplayAsync(
        GetConversationReplayRequest request)
    {
        return CompletableFuture.completedFuture(getConversationReplay(request));
    }

    @Override
    public DeleteRoundsResponse deleteRounds(DeleteRoundsRequest request)
    {
        return DeleteRoundsResponse.newBuilder()
            .setBase(errorBase(ConversationErrorCode.CONVERSATION_ERROR_CODE_INVALID_REQUEST_VALUE,
                "Round deletion is not implemented yet."))
            .setData(DeleteRoundsResult.getDefaultInstance())
            .build();
    }

    @Override
    public CompletableFuture<DeleteRoundsResponse> deleteRoundsAsync(DeleteRoundsRequest request)
    {
        return CompletableFuture.completedFuture(deleteRounds(request));
    }

    private ifl.agentbreaker.conversationmanager.rpc.ConversationRound toProtoRound(
        SaveConversationRoundRequest request)
    {
        ifl.agentbreaker.conversationmanager.rpc.ConversationRound.Builder conversationRound =
            ifl.agentbreaker.conversationmanager.rpc.ConversationRound.newBuilder()
            .setConversationId(request.getConversationId())
            .setRoundNumber(request.getRoundNumber())
            .setUserRequest(request.getUserRequest())
            .addAllTurns(request.getTurnsList())
            .setStatus(request.getStatus())
            .setErrorMessage(request.getErrorMessage())
            .setStartTime(request.getStartTime())
            .setEndTime(request.getEndTime());
        if (request.hasFinalAnswer())
            conversationRound.setFinalAnswer(request.getFinalAnswer());
        return conversationRound.build();
    }

    private ConversationRoundSummary toSummary(ConversationRound round)
    {
        ConversationRoundSummary.Builder summary = ConversationRoundSummary.newBuilder()
            .setConversationId(round.getConversationId())
            .setRoundNumber(round.getRoundNumber())
            .setUserRequest(UserRequest.newBuilder().setContent(round.getUserRequestContent()))
            .setStatus(switch (round.getStatus())
            {
                case COMPLETED -> RoundStatus.ROUND_STATUS_COMPLETED;
                case FAILED -> RoundStatus.ROUND_STATUS_FAILED;
                case CANCELLED -> RoundStatus.ROUND_STATUS_CANCELLED;
            })
        .setTurnCount(round.getTurnCount())
            .setErrorMessage(round.getErrorMessage())
            .setStartTime(round.getStartTime().getTime())
            .setEndTime(round.getEndTime().getTime());
        if (round.getFinalAnswerContent() != null)
            summary.setFinalAnswer(AssistantAnswer.newBuilder()
                .setContent(round.getFinalAnswerContent())
                .setSourceTurnNumber(round.getFinalSourceTurnNumber()));
        return summary.build();
    }

    private ResponseBase successBase()
    {
        return ResponseBase.newBuilder().setCode(0).setSuccess(true).setMessage("").build();
    }

    private ResponseBase errorBase(int code, String message)
    {
        return ResponseBase.newBuilder().setCode(code).setSuccess(false).setMessage(message).build();
    }
}
