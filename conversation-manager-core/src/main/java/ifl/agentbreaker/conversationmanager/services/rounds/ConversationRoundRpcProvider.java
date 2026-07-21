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

/**
 * Dubbo boundary owned by Conversation Manager for Runner's history, replay, persistence, and
 * attachment-preparation calls. The provider is deliberately thin: authorization and durable
 * invariants stay in domain services, while this class translates expected business outcomes into
 * the two-field protobuf response envelope.
 */
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

    /**
     * Keeps the shared Round RPC surface explicit: Conversation creation belongs to the HTTP
     * Conversation service, so Runner callers receive a typed contract error instead of creating
     * an object without the authenticated HTTP lifecycle and title/list metadata.
     *
     * @param request ignored creation payload supplied by a generic RPC client
     * @return a response describing why this provider does not own creation
     */
    @Override
    public CreateConversationResponse createConversation(CreateConversationRequest request)
    {
        return CreateConversationResponse.newBuilder()
            .setBase(errorBase(ConversationErrorCode.CONVERSATION_ERROR_CODE_INVALID_REQUEST_VALUE,
                "CreateConversation is not exposed by this Round persistence provider."))
            .setData(ConversationAbstract.getDefaultInstance())
            .build();
    }

    /**
     * Adapts the synchronous unsupported operation to Dubbo's asynchronous method shape.
     *
     * @param request creation request forwarded to {@link #createConversation(CreateConversationRequest)}
     * @return an already completed future containing the typed error response
     */
    @Override
    public CompletableFuture<CreateConversationResponse> createConversationAsync(CreateConversationRequest request)
    {
        return CompletableFuture.completedFuture(createConversation(request));
    }

    /**
     * Persists the Runner's complete Round after validation and idempotency checks. The response
     * envelope is important because business validation failures (for example a stale Round
     * number or invalid attachment reference) are expected RPC outcomes, not transport failures.
     *
     * @param request authenticated user, Conversation, ordered Turns, and terminal Round state
     * @return success with the persisted Round projection, or a domain error envelope
     * @throws RuntimeException only for an infrastructure failure that cannot be represented by the
     *         current business error contract
     */
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

    /**
     * Exposes Round persistence through Dubbo's future-based overload while retaining exactly the
     * same validation and transaction boundary as the synchronous method.
     *
     * @param request complete Round persistence request
     * @return future completed with the synchronous operation's response
     */
    @Override
    public CompletableFuture<SaveConversationRoundResponse> saveConversationRoundAsync(
        SaveConversationRoundRequest request)
    {
        return CompletableFuture.completedFuture(saveConversationRound(request));
    }

    /**
     * Returns the compact history used by Runner to calculate the next Round number without
     * transferring every LLM/tool child row across the service boundary.
     *
     * @param request authenticated user and Conversation identity
     * @return ordered Round summaries, or an ownership/not-found error envelope
     */
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

    /**
     * Adapts compact history retrieval to the asynchronous Dubbo signature.
     *
     * @param request authenticated history query
     * @return future containing the same response as {@link #getConversationRoundHistory(GetConversationRoundHistoryRequest)}
     */
    @Override
    public CompletableFuture<GetConversationRoundHistoryResponse> getConversationRoundHistoryAsync(
        GetConversationRoundHistoryRequest request)
    {
        return CompletableFuture.completedFuture(getConversationRoundHistory(request));
    }

    /**
     * Rejects the high-volume full-turn contract until a dedicated paginated API is available;
     * silently returning partial data would make replay and audit consumers incorrect.
     *
     * @param request requested Conversation and user identity
     * @return typed unsupported-operation response
     */
    @Override
    public GetConversationTurnHistoryResponse getConversationTurnHistory(GetConversationTurnHistoryRequest request)
    {
        return GetConversationTurnHistoryResponse.newBuilder()
            .setBase(errorBase(ConversationErrorCode.CONVERSATION_ERROR_CODE_INVALID_REQUEST_VALUE,
                "FULL_TURNS history is not implemented yet."))
            .setData(ConversationTurnHistory.getDefaultInstance())
            .build();
    }

    /**
     * Adapts the explicit full-turn rejection to the asynchronous Dubbo signature.
     *
     * @param request full-turn history request
     * @return future containing the typed rejection response
     */
    @Override
    public CompletableFuture<GetConversationTurnHistoryResponse> getConversationTurnHistoryAsync(
        GetConversationTurnHistoryRequest request)
    {
        return CompletableFuture.completedFuture(getConversationTurnHistory(request));
    }

    /**
     * Reconstructs the normalized model context needed by Runner for the next model call. The
     * service performs ownership and boundary checks here so a caller cannot replay another
     * user's Conversation by guessing an ID.
     *
     * @param request user, Conversation, detail level, and replay boundary
     * @return normalized context messages or a typed replay error
     */
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

    /**
     * Adapts model-context replay to the asynchronous Dubbo signature.
     *
     * @param request replay query
     * @return future containing the replay response
     */
    @Override
    public CompletableFuture<GetConversationReplayResponse> getConversationReplayAsync(
        GetConversationReplayRequest request)
    {
        return CompletableFuture.completedFuture(getConversationReplay(request));
    }

    /**
     * Keeps Round deletion disabled until the tail-suffix and child-row invariants have a
     * dedicated implementation; accepting a partial delete would corrupt replay high-water marks.
     *
     * @param request deletion scope requested by the caller
     * @return typed unsupported-operation response
     */
    @Override
    public DeleteRoundsResponse deleteRounds(DeleteRoundsRequest request)
    {
        return DeleteRoundsResponse.newBuilder()
            .setBase(errorBase(ConversationErrorCode.CONVERSATION_ERROR_CODE_INVALID_REQUEST_VALUE,
                "Round deletion is not implemented yet."))
            .setData(DeleteRoundsResult.getDefaultInstance())
            .build();
    }

    /**
     * Adapts the explicit Round deletion rejection to the asynchronous Dubbo signature.
     *
     * @param request deletion request
     * @return future containing the typed rejection response
     */
    @Override
    public CompletableFuture<DeleteRoundsResponse> deleteRoundsAsync(DeleteRoundsRequest request)
    {
        return CompletableFuture.completedFuture(deleteRounds(request));
    }

    /**
     * Authorizes stable file IDs, verifies upload/processing state, and reserves the selected
     * resources for one Runner request. Only metadata, extracted text, or short-lived image URLs
     * cross this boundary; the file bytes remain owned by Conversation Manager/OSS.
     *
     * @param request user, Conversation, request correlation ID, and stable file references
     * @return per-file state plus all-ready/failed aggregate flags
     */
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

    /**
     * Adapts file preparation to Dubbo's asynchronous signature without losing its correlation ID.
     *
     * @param request file preparation request
     * @return future containing the preparation response
     */
    @Override
    public CompletableFuture<PrepareConversationFilesResponse> prepareConversationFilesAsync(
        PrepareConversationFilesRequest request)
    {
        return CompletableFuture.completedFuture(prepareConversationFiles(request));
    }

    /**
     * Projects the accepted save request into the small response message expected by Runner;
     * persistence internals and child-row IDs deliberately stay private to Conversation Manager.
     *
     * @param request validated request returned by the persistence service
     * @return response-safe Round projection
     */
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

    /**
     * Applies cheap request checks before any file query or reservation. This prevents duplicate
     * IDs from producing ambiguous reservation ownership and prevents a caller from probing files
     * through a Conversation it does not own.
     *
     * @param request file preparation request to validate
     * @throws IllegalArgumentException when identity, ownership, count, or ID invariants fail
     */
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

    /**
     * Maps the persisted file state to the Runner contract. A signed URL is generated only for a
     * READY image because document text is already extracted and signed URLs must not be persisted.
     *
     * @param fileResource owned file row selected for this request
     * @return protocol payload containing stable metadata and usable READY content
     */
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

    /**
     * Distinguishes retryable processing states from states that already require user action or a
     * new resource, allowing Runner to stop polling with an actionable error.
     *
     * @param status persisted file status
     * @return true when the status is terminal for this preparation attempt
     */
    private boolean isTerminalFileFailure(ConversationFileStatus status)
    {
        return status == ConversationFileStatus.CONVERSATION_FILE_STATUS_FAILED
            || status == ConversationFileStatus.CONVERSATION_FILE_STATUS_CANCELLED
            || status == ConversationFileStatus.CONVERSATION_FILE_STATUS_DELETE_REQUESTED
            || status == ConversationFileStatus.CONVERSATION_FILE_STATUS_DELETED
            || status == ConversationFileStatus.CONVERSATION_FILE_STATUS_EXPIRED;
    }

    /**
     * Builds a stable error envelope while preserving the request correlation ID used by Runner
     * logs and reservation cleanup.
     *
     * @param data partially built preparation result carrying the request ID
     * @param code domain error code
     * @param message client-safe explanation
     * @return preparation response with no usable file payload
     */
    private PrepareConversationFilesResponse prepareFilesError(
        PrepareConversationFilesResult.Builder data, ConversationErrorCode code, String message)
    {
        return PrepareConversationFilesResponse.newBuilder()
            .setBase(errorBase(code.getNumber(), message))
            .setData(data)
            .build();
    }

    /**
     * Converts a database Round to the compact history shape. The browser and Runner need visible
     * content and status, but not the internal table IDs or raw LLM payloads.
     *
     * @param round persisted Round entity
     * @return compact history summary
     */
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
            .setStartTime(round.getStartTime().toEpochMilli())
            .setEndTime(round.getEndTime().toEpochMilli());
        if (round.getFinalAnswerContent() != null)
            summary.setFinalAnswer(AssistantAnswer.newBuilder()
                .setContent(round.getFinalAnswerContent())
                .setSourceTurnNumber(round.getFinalSourceTurnNumber()));
        return summary.build();
    }

    /**
     * Creates the common success envelope so every RPC consumer can branch on one stable field
     * before reading its typed data payload.
     *
     * @return successful response metadata with no user-facing message
     */
    private ResponseBase successBase()
    {
        return ResponseBase.newBuilder().setCode(0).setSuccess(true).setMessage("").build();
    }

    /**
     * Creates the common domain-error envelope. Keeping the code/message in {@code base} avoids
     * transport-level exceptions for expected validation, ownership, and state conflicts.
     *
     * @param code stable domain error code
     * @param message client-safe diagnostic message
     * @return failed response metadata
     */
    private ResponseBase errorBase(int code, String message)
    {
        return ResponseBase.newBuilder().setCode(code).setSuccess(false).setMessage(message).build();
    }
}
