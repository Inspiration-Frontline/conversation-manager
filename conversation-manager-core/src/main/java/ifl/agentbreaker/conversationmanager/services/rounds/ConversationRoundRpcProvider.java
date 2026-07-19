package ifl.agentbreaker.conversationmanager.services.rounds;

import ifl.agentbreaker.commons.api.dto.ResponseBase;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.ConversationRoundHistoryResult;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.ConversationReplayResult;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationRound;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.FileResource;
import ifl.agentbreaker.conversationmanager.config.ConversationFileProperties;
import ifl.agentbreaker.conversationmanager.dao.ConversationMapper;
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
import ifl.agentbreaker.conversationmanager.rpc.ConversationFileKind;
import ifl.agentbreaker.conversationmanager.rpc.ConversationFileStatus;
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
import ifl.agentbreaker.conversationmanager.rpc.PrepareConversationFilesRequest;
import ifl.agentbreaker.conversationmanager.rpc.PrepareConversationFilesResponse;
import ifl.agentbreaker.conversationmanager.rpc.PrepareConversationFilesResult;
import ifl.agentbreaker.conversationmanager.rpc.PreparedConversationFile;
import ifl.agentbreaker.conversationmanager.rpc.RoundStatus;
import ifl.agentbreaker.conversationmanager.rpc.SaveConversationRoundRequest;
import ifl.agentbreaker.conversationmanager.rpc.SaveConversationRoundResponse;
import ifl.agentbreaker.conversationmanager.services.files.ConversationFileService;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.CompletableFuture;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@DubboService
public class ConversationRoundRpcProvider implements ConversationRpcService
{
    @Autowired
    private ConversationRoundService conversationRoundService;

    @Autowired
    private ConversationFileService conversationFileService;

    @Autowired
    private ConversationFileProperties conversationFileProperties;

    @Autowired
    private ConversationMapper conversationMapper;

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

    @Override
    public PrepareConversationFilesResponse prepareConversationFiles(PrepareConversationFilesRequest request)
    {
        PrepareConversationFilesResult.Builder data = PrepareConversationFilesResult.newBuilder()
            .setRequestId(request.getRequestId());
        try
        {
            validatePrepareConversationFiles(request);
            List<FileResource> fileResources = conversationFileService.listOwnedFiles(
                request.getFileIdsList(), request.getUserId());
            if (fileResources.size() != request.getFileIdsCount())
                return prepareFilesError(data, ConversationErrorCode.CONVERSATION_ERROR_CODE_FILE_NOT_FOUND,
                    "One or more files do not exist.");

            long totalBytes = 0;
            for (FileResource fileResource : fileResources)
            {
                if (fileResource.getConfirmedTime() == null)
                    return prepareFilesError(data, ConversationErrorCode.CONVERSATION_ERROR_CODE_INVALID_FILE_SELECTION,
                        "Every selected file must have a confirmed upload.");
                totalBytes += fileResource.getFileSize();
            }
            if (totalBytes > conversationFileProperties.getMaxTotalBytesPerMessage())
                return prepareFilesError(data, ConversationErrorCode.CONVERSATION_ERROR_CODE_INVALID_FILE_SELECTION,
                    "The selected files exceed the total size limit.");
            if (!conversationFileService.reserveFilesForRequest(
                request.getFileIdsList(),
                request.getUserId(),
                request.getConversationId(),
                request.getRequestId()))
                return prepareFilesError(data, ConversationErrorCode.CONVERSATION_ERROR_CODE_INVALID_FILE_SELECTION,
                    "One or more files are reserved by another request.");

            boolean allReady = true;
            boolean anyFailed = false;
            for (FileResource fileResource : fileResources)
            {
                PreparedConversationFile preparedFile = toPreparedFile(fileResource);
                data.addFiles(preparedFile);
                allReady &= preparedFile.getStatus() == ConversationFileStatus.CONVERSATION_FILE_STATUS_READY;
                anyFailed |= isTerminalFileFailure(preparedFile.getStatus());
            }
            data.setAllReady(allReady);
            data.setAnyFailed(anyFailed);
            return PrepareConversationFilesResponse.newBuilder().setBase(successBase()).setData(data).build();
        }
        catch (IllegalArgumentException e)
        {
            return prepareFilesError(data, ConversationErrorCode.CONVERSATION_ERROR_CODE_INVALID_FILE_SELECTION,
                e.getMessage());
        }
    }

    @Override
    public CompletableFuture<PrepareConversationFilesResponse> prepareConversationFilesAsync(
        PrepareConversationFilesRequest request)
    {
        return CompletableFuture.completedFuture(prepareConversationFiles(request));
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

    private void validatePrepareConversationFiles(PrepareConversationFilesRequest request)
    {
        if (request.getUserId() <= 0
            || request.getConversationId().isBlank()
            || request.getRequestId().isBlank()
            || request.getFileIdsCount() <= 0
            || request.getFileIdsCount() > conversationFileProperties.getMaxCountPerMessage())
            throw new IllegalArgumentException("The file preparation request is invalid.");
        Set<String> uniqueFileIds = new HashSet<>(request.getFileIdsList());
        if (uniqueFileIds.size() != request.getFileIdsCount() || uniqueFileIds.stream().anyMatch(String::isBlank))
            throw new IllegalArgumentException("File IDs must be non-empty and unique.");
        if (!conversationMapper.existsByIdAndUser(request.getConversationId(), request.getUserId()))
            throw new IllegalArgumentException("The conversation does not exist.");
    }

    private PreparedConversationFile toPreparedFile(FileResource fileResource)
    {
        ConversationFileStatus status = ConversationFileStatus.valueOf(
            "CONVERSATION_FILE_STATUS_" + fileResource.getStatus().name());
        ConversationFileKind kind = ConversationFileKind.valueOf(
            "CONVERSATION_FILE_KIND_" + fileResource.getKind().name());
        PreparedConversationFile.Builder preparedFile = PreparedConversationFile.newBuilder()
            .setFileId(fileResource.getFileId())
            .setOriginalFilename(fileResource.getOriginalFilename())
            .setMimeType(fileResource.getDetectedMimeType() == null
                ? fileResource.getDeclaredMimeType()
                : fileResource.getDetectedMimeType())
            .setFileSize(fileResource.getFileSize())
            .setKind(kind)
            .setStatus(status)
            .setRevision(fileResource.getStatusRevision())
            .setErrorCode(fileResource.getErrorCode())
            .setErrorMessage(fileResource.getErrorMessage())
            .setExtractionTruncated(fileResource.isExtractionTruncated());
        if (fileResource.getSha256() != null)
            preparedFile.setSha256(fileResource.getSha256());
        if (fileResource.getStatus() == ifl.agentbreaker.conversationmanager.domain.constants.ConversationFileStatus.READY)
        {
            if (fileResource.getKind() == ifl.agentbreaker.conversationmanager.domain.constants.ConversationFileKind.IMAGE)
                preparedFile.setDownloadUrl(conversationFileService.createSignedGetUrl(fileResource));
            else if (fileResource.getExtractedText() != null)
                preparedFile.setExtractedText(fileResource.getExtractedText());
        }
        return preparedFile.build();
    }

    private boolean isTerminalFileFailure(ConversationFileStatus status)
    {
        return status == ConversationFileStatus.CONVERSATION_FILE_STATUS_FAILED
            || status == ConversationFileStatus.CONVERSATION_FILE_STATUS_CANCELLED
            || status == ConversationFileStatus.CONVERSATION_FILE_STATUS_DELETE_REQUESTED
            || status == ConversationFileStatus.CONVERSATION_FILE_STATUS_DELETED
            || status == ConversationFileStatus.CONVERSATION_FILE_STATUS_EXPIRED;
    }

    private PrepareConversationFilesResponse prepareFilesError(
        PrepareConversationFilesResult.Builder data, ConversationErrorCode code, String message)
    {
        return PrepareConversationFilesResponse.newBuilder()
            .setBase(errorBase(code.getNumber(), message))
            .setData(data)
            .build();
    }

    private ConversationRoundSummary toSummary(ConversationRound round)
    {
        ConversationRoundSummary.Builder summary = ConversationRoundSummary.newBuilder()
            .setConversationId(round.getConversationId())
            .setRoundNumber(round.getRoundNumber())
            .setUserRequest(conversationRoundService.toProtoUserRequest(round))
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
