package ifl.agentbreaker.conversationmanager.services.rounds;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationRoundFileMapper;
import ifl.agentbreaker.conversationmanager.dao.FileCleanupTaskMapper;
import ifl.agentbreaker.conversationmanager.dao.FileResourceMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationLlmCallMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationLlmRequestMessageMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationLlmRequestMessageToolCallMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationLlmResponseToolCallMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationLlmToolDefinitionMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationRoundMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationTurnMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationToolCallExecutionMapper;
import ifl.agentbreaker.conversationmanager.domain.constants.ConversationRoundStatus;
import ifl.agentbreaker.conversationmanager.domain.constants.ConversationFileStatus;
import ifl.agentbreaker.conversationmanager.domain.constants.ConversationTurnStatus;
import ifl.agentbreaker.conversationmanager.domain.constants.LlmMessageRole;
import ifl.agentbreaker.conversationmanager.domain.constants.LlmMessageStorageMode;
import ifl.agentbreaker.conversationmanager.domain.constants.ToolCallExecutionStatus;
import ifl.agentbreaker.conversationmanager.domain.constants.ToolSourceType;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.ConversationReplayResult;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.ConversationRoundHistoryResult;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.RoundHistoryView;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.RoundFileHistory;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.Conversation;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationLlmCall;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationLlmRequestMessage;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationLlmRequestMessageToolCall;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationLlmResponseToolCall;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationLlmToolDefinition;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationRound;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationTurn;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationToolCallExecution;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.EntityBase;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.FileResource;
import ifl.agentbreaker.conversationmanager.support.ConversationTitleManager;
import ifl.agentbreaker.conversationmanager.rpc.ConversationErrorCode;
import ifl.agentbreaker.conversationmanager.rpc.ContentPart;
import ifl.agentbreaker.conversationmanager.rpc.FileUrl;
import ifl.agentbreaker.conversationmanager.rpc.FunctionCall;
import ifl.agentbreaker.conversationmanager.rpc.MessageRole;
import ifl.agentbreaker.conversationmanager.rpc.RoundStatus;
import ifl.agentbreaker.conversationmanager.rpc.LlmCall;
import ifl.agentbreaker.conversationmanager.rpc.LlmConversationMessage;
import ifl.agentbreaker.conversationmanager.rpc.LlmRequest;
import ifl.agentbreaker.conversationmanager.rpc.LlmResponse;
import ifl.agentbreaker.conversationmanager.rpc.SaveConversationRoundRequest;
import ifl.agentbreaker.conversationmanager.rpc.TokenUsage;
import ifl.agentbreaker.conversationmanager.rpc.ToolCall;
import ifl.agentbreaker.conversationmanager.rpc.ToolCallExecution;
import ifl.agentbreaker.conversationmanager.rpc.ToolDefinition;
import ifl.agentbreaker.conversationmanager.rpc.UserRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import stark.dataworks.boot.autoconfig.web.LogArgumentsAndResponse;
import stark.dataworks.boot.web.ServiceResponse;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Owns the durable Conversation Round boundary. A Round is persisted as one transaction spanning
 * the parent high-water mark, attachment links, Turns, LLM calls, messages, and Tool evidence;
 * keeping that unit here prevents Runner retries from creating half-visible history.
 */
@Service
@LogArgumentsAndResponse
public class ConversationRoundService
{
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

    @Autowired
    private ConversationLlmRequestMessageToolCallMapper conversationLlmRequestMessageToolCallMapper;

    @Autowired
    private ConversationLlmToolDefinitionMapper conversationLlmToolDefinitionMapper;

    @Autowired
    private ConversationLlmResponseToolCallMapper conversationLlmResponseToolCallMapper;

    @Autowired
    private ConversationToolCallExecutionMapper conversationToolCallExecutionMapper;

    @Autowired
    private ConversationRoundFileMapper conversationRoundFileMapper;

    @Autowired
    private FileResourceMapper fileResourceMapper;

    @Autowired
    private FileCleanupTaskMapper fileCleanupTaskMapper;

    @Autowired
    private ConversationRoundValidator conversationRoundValidator;

    @Autowired
    private ConversationRoundPayloadHasher conversationRoundPayloadHasher;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ConversationMutationLock conversationMutationLock;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private static final int ERROR_CONVERSATION_NOT_FOUND = 2002;

    /**
     * Loads active Round summaries after verifying ownership. Runner uses the returned high-water
     * mark to assign the next Round number, so this method must never expose another user's rows.
     *
     * @param userId authenticated caller identity
     * @param conversationId Conversation whose compact history is requested
     * @return latest high-water mark and ordered active Rounds
     * @throws RoundPersistenceException when the Conversation is missing or not owned by the user
     */
    public ConversationRoundHistoryResult getHistory(long userId, String conversationId)
    {
        Long latestRoundNumber = conversationMapper.getLatestRoundNumberByIdAndUser(conversationId, userId);
        if (latestRoundNumber == null)
            throw new RoundPersistenceException(ERROR_CONVERSATION_NOT_FOUND, "Conversation does not exist.");
        return new ConversationRoundHistoryResult(
            latestRoundNumber, conversationRoundMapper.listActiveRounds(conversationId));
    }

    /**
     * Builds the browser history view from the compact Round rows and attachment links. Text is
     * recovered from stored content parts for rows written before the scalar compatibility column
     * was populated, allowing old file conversations to remain readable after refresh.
     *
     * @param userId authenticated browser identity
     * @param conversationId Conversation selected in the UI
     * @return service envelope containing visible messages and attachment summaries
     */
    public ServiceResponse<RoundHistoryView> getHttpHistory(long userId, String conversationId)
    {
        try
        {
            ConversationRoundHistoryResult history = getHistory(userId, conversationId);
            Map<Long, List<RoundFileHistory>> filesByRound = conversationRoundFileMapper
                .listRoundFiles(conversationId)
                .stream()
                .collect(Collectors.groupingBy(RoundFileHistory::roundNumber));
            return ServiceResponse.buildSuccessResponse(new RoundHistoryView(
                conversationId,
                history.latestRoundNumber(),
                history.rounds().stream().map(round -> new RoundHistoryView.RoundView(
                    round.getRoundNumber(), extractTextContent(round), round.getFinalAnswerContent(),
                    round.getStatus().name(), round.getErrorMessage(), round.getTurnCount(),
                    round.getStartTime().getTime(), round.getEndTime().getTime(),
                    filesByRound.getOrDefault(round.getRoundNumber(), List.of()).stream()
                        .map(file -> new RoundHistoryView.FileView(
                            file.fileId(), file.originalFilename(), file.mimeType(), file.fileSize(),
                            file.kind(), file.status()))
                        .toList())).toList()));
        }
        catch (RoundPersistenceException e)
        {
            return ServiceResponse.buildErrorResponse(e.getCode(), e.getMessage());
        }
    }

    /**
     * Reconstructs the normalized model context required to replay a completed Round. Replay is
     * deliberately assembled from durable LLM rows rather than cached SDK objects, so a new Runner
     * process can continue a Conversation after restart.
     *
     * @param userId authenticated caller identity
     * @param conversationId Conversation to replay
     * @param endRoundNumber inclusive replay boundary
     * @return normalized context messages for the model adapter
     * @throws RoundPersistenceException when ownership or the boundary is invalid
     */
    public ConversationReplayResult getModelContext(long userId, String conversationId, long endRoundNumber)
    {
        Long latestRoundNumber = conversationMapper.getLatestRoundNumberByIdAndUser(conversationId, userId);
        if (latestRoundNumber == null)
            throw new RoundPersistenceException(ERROR_CONVERSATION_NOT_FOUND, "Conversation does not exist.");
        if (endRoundNumber <= 0 || endRoundNumber > latestRoundNumber)
            throw new RoundPersistenceException(
                ConversationErrorCode.CONVERSATION_ERROR_CODE_INVALID_REQUEST_VALUE,
                "end_round_number must reference an assigned round.");

        ConversationRound boundaryRound = conversationRoundMapper.getRound(conversationId, endRoundNumber);
        if (boundaryRound == null || boundaryRound.isDeleted())
            throw new RoundPersistenceException(
                ConversationErrorCode.CONVERSATION_ERROR_CODE_ROUND_NOT_FOUND_VALUE,
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

        Map<Long, List<ConversationLlmRequestMessageToolCall>> toolCallsByMessageId =
            conversationLlmRequestMessageToolCallMapper.listRequestMessageToolCallsForRound(completedRound.getId())
                .stream().collect(Collectors.groupingBy(
                    ConversationLlmRequestMessageToolCall::getRequestMessageId));
        List<LlmConversationMessage> contextMessages = conversationLlmRequestMessageMapper
            .listRequestMessagesForRound(completedRound.getId()).stream()
            .map(message -> toProtoMessage(message, toolCallsByMessageId.getOrDefault(message.getId(), List.of())))
            .collect(Collectors.toCollection(ArrayList::new));
        contextMessages.add(LlmConversationMessage.newBuilder()
            .setRole(MessageRole.MESSAGE_ROLE_ASSISTANT)
            .setContent(conversationLlmCall.getResponseContent())
            .build());
        return new ConversationReplayResult(conversationId, List.copyOf(contextMessages));
    }

    /**
     * Converts one persisted request message and its Tool calls into the neutral replay protobuf
     * shape, preserving role/content ordering while hiding database identifiers.
     *
     * @param message persisted LLM request message
     * @param toolCalls calls belonging to that message
     * @return replay-safe protobuf message
     */
    private LlmConversationMessage toProtoMessage(
        ConversationLlmRequestMessage message,
        List<ConversationLlmRequestMessageToolCall> toolCalls)
    {
        LlmConversationMessage.Builder builder = LlmConversationMessage.newBuilder()
            .setRole(switch (message.getRole())
            {
                case SYSTEM -> MessageRole.MESSAGE_ROLE_SYSTEM;
                case USER -> MessageRole.MESSAGE_ROLE_USER;
                case ASSISTANT -> MessageRole.MESSAGE_ROLE_ASSISTANT;
                case TOOL -> MessageRole.MESSAGE_ROLE_TOOL;
                case DEVELOPER -> MessageRole.MESSAGE_ROLE_DEVELOPER;
            })
            .setContent(message.getContent() == null ? "" : message.getContent())
            .addAllToolCalls(toolCalls.stream().map(toolCall -> ToolCall.newBuilder()
                .setId(toolCall.getToolCallId())
                .setType(toolCall.getType())
                .setFunction(FunctionCall.newBuilder()
                    .setName(toolCall.getFunctionName())
                    .setArguments(toolCall.getArguments()))
                .build()).toList())
            .setToolCallId(message.getToolCallId() == null ? "" : message.getToolCallId());
        builder.addAllContentParts(deserializeContentParts(message.getContentParts()));
        return builder.build();
    }

    /**
     * Validates, serializes, and atomically persists one complete Round.
     *
     * <p>The Redis mutation lock is deliberately acquired before the PostgreSQL transaction starts.
     * TransactionTemplate keeps that boundary explicit without requiring a separate command
     * service solely to trigger Spring's transactional proxy.</p>
     */
    /**
     * Validates, hashes, and persists one idempotent Round mutation under the distributed
     * Conversation lock. The lock is acquired before the SQL transaction so two Runner retries
     * cannot both observe the same high-water mark and allocate one Round number.
     *
     * @param request complete terminal or failure Round emitted by Runner
     * @return the accepted request, used to construct the RPC response projection
     * @throws RoundPersistenceException for expected validation/conflict/domain failures
     */
    public SaveConversationRoundRequest save(SaveConversationRoundRequest request)
    {
        conversationRoundValidator.validatePhaseFour(request);
        String payloadHash = conversationRoundPayloadHasher.hash(request);
        try (ConversationMutationLock.LockHandle ignored =
                 conversationMutationLock.acquire(request.getConversationId()))
        {
            SaveConversationRoundRequest savedRequest = transactionTemplate.execute(
                transactionStatus -> saveInTransaction(request, payloadHash));
            if (savedRequest == null)
                throw new IllegalStateException("Round persistence transaction returned no result.");
            return savedRequest;
        }
    }

    /**
     * Executes the database portion of Round persistence in one transaction. Existing matching
     * payloads are treated as idempotent retries; different payloads for the same number are
     * rejected rather than silently overwriting history.
     *
     * @param request validated Round mutation
     * @param payloadHash deterministic request hash used for idempotency
     * @return request after all child rows and the Conversation high-water mark are committed
     */
    private SaveConversationRoundRequest saveInTransaction(SaveConversationRoundRequest request, String payloadHash)
    {
        Conversation conversation = conversationMapper.lockConversationByIdAndUser(
            request.getConversationId(), request.getUserId());
        if (conversation == null)
            throw error(ConversationErrorCode.CONVERSATION_ERROR_CODE_CONVERSATION_NOT_FOUND,
                "Conversation does not exist.");

        long highWater = conversation.getLatestRoundNumber();
        ConversationRound existing = conversationRoundMapper.getRound(
            request.getConversationId(), request.getRoundNumber());
        if (request.getRoundNumber() <= highWater)
        {
            if (existing == null || existing.isDeleted())
                throw error(ConversationErrorCode.CONVERSATION_ERROR_CODE_ROUND_NUMBER_RETIRED,
                    "Round number has already been retired.");
            if (existing.getPayloadHashVersion() != ConversationRoundPayloadHasher.CURRENT_VERSION
                || !payloadHash.equals(existing.getPayloadHash()))
                throw error(ConversationErrorCode.CONVERSATION_ERROR_CODE_ROUND_NUMBER_CONFLICT,
                    "Round number already contains different persisted content.");
            return request;
        }

        if (request.getRoundNumber() != highWater + 1)
            throw error(ConversationErrorCode.CONVERSATION_ERROR_CODE_INVALID_REQUEST,
                "round_number must equal the persisted high-water mark plus one.");

        ConversationRound savedRound = conversationRoundMapper.insertRound(toRound(request, payloadHash));
        if (savedRound == null)
            throw new IllegalStateException("Round insert returned no row.");

        List<FileResource> roundFiles = persistRoundFiles(request, savedRound.getId());
        persistTurnsAndChildren(request, savedRound.getId());

        // TODO: Replace repeated cross-Round FULL_SNAPSHOT rows with context_id plus the current
        // Round delta when the deferred Context checkpoint/compaction model is designed.

        // Keep auto-title in the high-water transaction so failed Rounds never rename a Conversation.
        String visibleUserMessage = extractTextContent(request.getUserRequest());
        String automaticTitle = StringUtils.hasText(visibleUserMessage)
            ? ConversationTitleManager.deriveFromFirstUserMessage(visibleUserMessage)
            : roundFiles.isEmpty()
                ? ConversationTitleManager.DEFAULT_TITLE
                : ConversationTitleManager.deriveFromAttachmentFilename(roundFiles.get(0).getOriginalFilename());
        if (conversationMapper.advanceLatestRoundNumber(
            request.getConversationId(), request.getUserId(), request.getRoundNumber(),
            automaticTitle, ConversationTitleManager.DEFAULT_TITLE) != 1)
            throw new IllegalStateException("Failed to advance conversation round high-water mark.");

        return request;
    }

    /**
     * Persists every Turn-owned table with one batch statement per table.
     *
     * <p>PostgreSQL does not contractually preserve input order for RETURNING rows. Associations
     * are therefore rebuilt from persisted business keys instead of list positions.</p>
     */
    /**
     * Validates stable file references, creates all Round links in one batch, and cancels orphan
     * cleanup tasks. The loop only normalizes IDs; it does not issue one SQL write per file.
     *
     * @param request Round containing stable AgentBreaker file URLs
     * @param roundId newly inserted parent Round ID
     * @return owned file resources used for title fallback and state checks
     */
    private List<FileResource> persistRoundFiles(SaveConversationRoundRequest request, long roundId)
    {
        Set<String> fileIds = new LinkedHashSet<>();
        int filePartCount = 0;
        for (ContentPart contentPart : request.getUserRequest().getContentPartsList())
        {
            if (contentPart.getType().equals("text"))
                continue;
            filePartCount++;
            if (!contentPart.hasFileUrl())
                throw error(ConversationErrorCode.CONVERSATION_ERROR_CODE_INVALID_FILE_SELECTION,
                    "Every Round file part must contain a stable AgentBreaker file URL.");
            String url = contentPart.getFileUrl().getUrl();
            String prefix = "agentbreaker-file://";
            if (!url.startsWith(prefix) || url.length() == prefix.length()
                || !fileIds.add(url.substring(prefix.length())))
                throw error(ConversationErrorCode.CONVERSATION_ERROR_CODE_INVALID_FILE_SELECTION,
                    "Round file parts must contain unique stable AgentBreaker file URLs.");
        }
        if (fileIds.isEmpty())
            return List.of();

        List<FileResource> fileResources = fileResourceMapper.listOwnedFileResources(fileIds, request.getUserId());
        if (fileResources.size() != filePartCount
            || fileResources.stream().anyMatch(fileResource -> fileResource.getConfirmedTime() == null))
            throw error(ConversationErrorCode.CONVERSATION_ERROR_CODE_INVALID_FILE_SELECTION,
                "Every Round file must exist, be owned by the user, and have a confirmed upload.");
        if (request.getStatus() == RoundStatus.ROUND_STATUS_COMPLETED
            && fileResources.stream().anyMatch(fileResource -> fileResource.getStatus() != ConversationFileStatus.READY))
            throw error(ConversationErrorCode.CONVERSATION_ERROR_CODE_INVALID_FILE_SELECTION,
                "A completed Round can reference only READY files.");

        List<Long> fileResourceIds = fileResources.stream().map(FileResource::getId).toList();
        requireAffectedRows(
            "Round file",
            fileResourceIds.size(),
            conversationRoundFileMapper.insertRoundFiles(roundId, request.getUserId(), fileResourceIds));
        for (Long fileResourceId : fileResourceIds)
            fileCleanupTaskMapper.cancelByFileResourceId(fileResourceId);
        return fileResources;
    }

    /**
     * Persists Turns and all LLM/Tool child rows using one batch operation per table, then rebuilds
     * associations from returned business keys because PostgreSQL does not promise RETURNING order.
     *
     * @param request complete Runner capture
     * @param roundId parent Round database ID
     */
    private void persistTurnsAndChildren(SaveConversationRoundRequest request, long roundId)
    {
        if (request.getTurnsList().isEmpty())
            return;

        List<ConversationTurn> turns = new ArrayList<>();
        for (ifl.agentbreaker.conversationmanager.rpc.ConversationTurn sourceTurn : request.getTurnsList())
            turns.add(toTurn(sourceTurn, roundId, request.getUserId()));
        List<ConversationTurn> savedTurns = conversationTurnMapper.insertTurns(turns);
        requireReturnedRows("Turn", turns.size(), savedTurns);

        List<ConversationLlmCall> llmCalls = new ArrayList<>();
        for (ConversationTurn savedTurn : savedTurns)
        {
            int sourceIndex = Math.toIntExact(savedTurn.getTurnNumber() - 1);
            llmCalls.add(toLlmCall(
                request.getTurns(sourceIndex).getLlmCall(), savedTurn.getId(), request.getUserId()));
        }
        List<ConversationLlmCall> savedLlmCalls = conversationLlmCallMapper.insertLlmCalls(llmCalls);
        requireReturnedRows("LLM call", llmCalls.size(), savedLlmCalls);

        Map<Long, ConversationLlmCall> llmCallsByTurnId = savedLlmCalls.stream().collect(
            Collectors.toMap(ConversationLlmCall::getTurnId, llmCall -> llmCall));
        List<TurnPersistenceContext> contexts = new ArrayList<>();
        for (ConversationTurn savedTurn : savedTurns)
        {
            ConversationLlmCall savedLlmCall = llmCallsByTurnId.get(savedTurn.getId());
            if (savedLlmCall == null)
                throw new IllegalStateException("LLM call batch did not return a row for every Turn.");
            int sourceIndex = Math.toIntExact(savedTurn.getTurnNumber() - 1);
            contexts.add(new TurnPersistenceContext(
                request.getTurns(sourceIndex), savedTurn, savedLlmCall));
        }

        persistToolDefinitions(contexts, request.getUserId());
        persistRequestMessagesAndToolCalls(contexts, request.getUserId());
        persistResponseToolCallsAndExecutions(contexts, request.getUserId());
    }

    /**
     * Maps the RPC Round request into the parent database entity while retaining structured
     * content parts. The database requires scalar text and JSON parts to be mutually exclusive;
     * attachment text therefore remains inside JSON parts and is projected back for HTTP history
     * and automatic titles.
     *
     * @param request validated RPC Round
     * @param payloadHash idempotency hash calculated before locking
     * @return populated parent Round entity
     */
    private ConversationRound toRound(
        SaveConversationRoundRequest request, String payloadHash)
    {
        ConversationRound conversationRound = new ConversationRound();
        applyAudit(conversationRound, request.getUserId());
        conversationRound.setConversationId(request.getConversationId());
        conversationRound.setRoundNumber(request.getRoundNumber());
        // The database contract deliberately stores scalar text and structured parts exclusively.
        // Attachment messages still expose their text through extractTextContent(...) for titles
        // and HTTP history, but the persisted row keeps that text inside the JSON parts column.
        boolean hasUserRequestParts = request.getUserRequest().getContentPartsCount() > 0;
        conversationRound.setUserRequestContent(hasUserRequestParts
            ? null
            : extractTextContent(request.getUserRequest()));
        conversationRound.setUserRequestContentParts(hasUserRequestParts
            ? serializeContentParts(request.getUserRequest().getContentPartsList())
            : null);
        conversationRound.setFinalAnswerContent(request.hasFinalAnswer()
            && StringUtils.hasText(request.getFinalAnswer().getContent())
            ? request.getFinalAnswer().getContent()
            : null);
        conversationRound.setFinalAnswerContentParts(request.hasFinalAnswer()
            ? serializeContentParts(request.getFinalAnswer().getContentPartsList())
            : null);
        conversationRound.setFinalSourceTurnNumber(
            request.hasFinalAnswer() ? request.getFinalAnswer().getSourceTurnNumber() : null);
        conversationRound.setStatus(switch (request.getStatus())
        {
            case ROUND_STATUS_COMPLETED -> ConversationRoundStatus.COMPLETED;
            case ROUND_STATUS_FAILED -> ConversationRoundStatus.FAILED;
            case ROUND_STATUS_CANCELLED -> ConversationRoundStatus.CANCELLED;
            default -> throw new IllegalArgumentException("Unsupported round status.");
        });
        conversationRound.setErrorMessage(request.getErrorMessage());
        conversationRound.setStartTime(new Date(request.getStartTime()));
        conversationRound.setEndTime(new Date(request.getEndTime()));
        conversationRound.setPayloadHashVersion(ConversationRoundPayloadHasher.CURRENT_VERSION);
        conversationRound.setPayloadHash(payloadHash);
        conversationRound.setDeleted(false);
        return conversationRound;
    }

    /**
     * Maps one RPC Turn and its audit identity into a persisted child entity.
     *
     * @param source RPC Turn emitted by Runner
     * @param roundId parent database Round ID
     * @param userId authenticated owner written to audit columns
     * @return persisted Turn entity ready for batch insertion
     */
    private ConversationTurn toTurn( ifl.agentbreaker.conversationmanager.rpc.ConversationTurn source,
                                     long roundId, long userId)
    {
        ConversationTurn conversationTurn = new ConversationTurn();
        applyAudit(conversationTurn, userId);
        conversationTurn.setRoundId(roundId);
        conversationTurn.setTurnNumber(source.getTurnNumber());
        conversationTurn.setAgentId(source.getAgentIdentity().getAgentId());
        conversationTurn.setAgentName(source.getAgentIdentity().getName());
        conversationTurn.setAgentVersion(source.getAgentIdentity().getVersion());
        conversationTurn.setStatus(switch (source.getStatus())
        {
            case TURN_STATUS_COMPLETED -> ConversationTurnStatus.COMPLETED;
            case TURN_STATUS_FAILED -> ConversationTurnStatus.FAILED;
            case TURN_STATUS_CANCELLED -> ConversationTurnStatus.CANCELLED;
            default -> throw new IllegalArgumentException("Unsupported turn status.");
        });
        conversationTurn.setErrorMessage(source.getErrorMessage());
        conversationTurn.setStartTime(new Date(source.getStartTime()));
        conversationTurn.setEndTime(new Date(source.getEndTime()));
        return conversationTurn;
    }

    /**
     * Maps one model call, including provider metadata, request/response storage mode, usage, and
     * raw audit payloads, into the durable LLM call row.
     *
     * @param source RPC LLM call
     * @param turnId parent Turn ID
     * @param userId authenticated owner written to audit columns
     * @return durable LLM call entity
     */
    private ConversationLlmCall toLlmCall(LlmCall source, long turnId, long userId)
    {
        LlmRequest llmRequest = source.getRequest();
        LlmResponse llmResponse = source.getResponse();
        ConversationLlmCall conversationLlmCall = new ConversationLlmCall();
        applyAudit(conversationLlmCall, userId);
        conversationLlmCall.setTurnId(turnId);
        conversationLlmCall.setProvider(llmRequest.getProvider());
        conversationLlmCall.setModel(llmRequest.getModel());
        conversationLlmCall.setRequestId(source.getRequestId());
        conversationLlmCall.setTraceId(source.getTraceId());
        conversationLlmCall.setMessageStorageMode(switch (llmRequest.getMessageStorageMode())
        {
            case LLM_MESSAGE_STORAGE_MODE_FULL_SNAPSHOT -> LlmMessageStorageMode.FULL_SNAPSHOT;
            case LLM_MESSAGE_STORAGE_MODE_APPEND_DELTA -> LlmMessageStorageMode.APPEND_DELTA;
            default -> throw new IllegalArgumentException("Unsupported LLM message storage mode.");
        });
        conversationLlmCall.setToolChoicePresent(llmRequest.hasToolChoice());
        conversationLlmCall.setResponseFormat(
            llmRequest.getResponseFormat().isEmpty() ? null : llmRequest.getResponseFormat());
        conversationLlmCall.setTemperature(llmRequest.hasTemperature() ? llmRequest.getTemperature() : null);
        conversationLlmCall.setMaxOutputTokens(
            llmRequest.hasMaxOutputTokens() ? llmRequest.getMaxOutputTokens() : null);
        conversationLlmCall.setRawRequest(llmRequest.hasRawRequest() ? llmRequest.getRawRequest() : null);
        conversationLlmCall.setStartTime(new Date(source.getStartTime()));
        conversationLlmCall.setEndTime(new Date(source.getEndTime()));
        conversationLlmCall.setResponseMessagePresent(llmResponse.hasMessage());
        conversationLlmCall.setResponseContent(llmResponse.getMessage().getContent());
        conversationLlmCall.setFinishReason(llmResponse.getFinishReason());
        conversationLlmCall.setUsagePresent(llmResponse.hasUsage());
        if (llmResponse.hasUsage())
        {
            TokenUsage tokenUsage = llmResponse.getUsage();
            conversationLlmCall.setPromptTokens(tokenUsage.getPromptTokens());
            conversationLlmCall.setCompletionTokens(tokenUsage.getCompletionTokens());
            conversationLlmCall.setTotalTokens(tokenUsage.getTotalTokens());
            conversationLlmCall.setCachedPromptTokens(tokenUsage.getCachedPromptTokens());
            conversationLlmCall.setReasoningTokens(tokenUsage.getReasoningTokens());
        }
        conversationLlmCall.setRawResponse(
            llmResponse.hasRawResponse() ? llmResponse.getRawResponse() : null);
        conversationLlmCall.setResponseErrorMessage(llmResponse.getErrorMessage());
        conversationLlmCall.setReasoningContent(
            llmResponse.hasReasoningContent() ? llmResponse.getReasoningContent() : null);
        return conversationLlmCall;
    }

    /**
     * Maps a normalized model request message into the durable audit table shape. Scalar content
     * and structured parts remain mutually exclusive, matching the validator's replay contract.
     *
     * @param source normalized RPC message
     * @param llmCallId parent LLM call ID
     * @param messageOrder zero-based order within that call
     * @param userId authenticated owner written to audit columns
     * @return durable request-message entity
     */
    private ConversationLlmRequestMessage toRequestMessage(LlmConversationMessage source, long llmCallId,
                                                            int messageOrder, long userId)
    {
        ConversationLlmRequestMessage conversationLlmRequestMessage = new ConversationLlmRequestMessage();
        applyAudit(conversationLlmRequestMessage, userId);
        conversationLlmRequestMessage.setLlmCallId(llmCallId);
        conversationLlmRequestMessage.setMessageOrder(messageOrder);
        conversationLlmRequestMessage.setRole(switch (source.getRole())
        {
            case MESSAGE_ROLE_SYSTEM -> LlmMessageRole.SYSTEM;
            case MESSAGE_ROLE_USER -> LlmMessageRole.USER;
            case MESSAGE_ROLE_ASSISTANT -> LlmMessageRole.ASSISTANT;
            case MESSAGE_ROLE_TOOL -> LlmMessageRole.TOOL;
            case MESSAGE_ROLE_DEVELOPER -> LlmMessageRole.DEVELOPER;
            default -> throw new IllegalArgumentException("Unsupported request message role.");
        });
        conversationLlmRequestMessage.setContent(StringUtils.hasText(source.getContent()) ? source.getContent() : null);
        conversationLlmRequestMessage.setContentParts(serializeContentParts(source.getContentPartsList()));
        conversationLlmRequestMessage.setToolCallId(
            source.getToolCallId().isEmpty() ? null : source.getToolCallId());
        return conversationLlmRequestMessage;
    }

    /**
     * Batch-persists the frozen Tool definitions used by all Turns in the Round so later replay
     * can explain exactly which schema the model saw, even if configuration changes.
     *
     * @param contexts source/projection pairs for the Round's Turns
     * @param userId authenticated owner written to audit columns
     */
    private void persistToolDefinitions(List<TurnPersistenceContext> contexts, long userId)
    {
        List<ConversationLlmToolDefinition> definitions = new ArrayList<>();
        for (TurnPersistenceContext context : contexts)
        {
            int toolOrder = 0;
            for (ToolDefinition source : context.sourceTurn().getLlmCall().getRequest().getToolsList())
            {
                ConversationLlmToolDefinition definition = new ConversationLlmToolDefinition();
                applyAudit(definition, userId);
                definition.setLlmCallId(context.llmCall().getId());
                definition.setToolOrder(toolOrder++);
                definition.setToolKey(source.getToolKey());
                definition.setToolName(source.getToolName());
                definition.setSourceType(switch (source.getSourceType())
                {
                    case TOOL_SOURCE_TYPE_INTERNAL -> ToolSourceType.INTERNAL;
                    case TOOL_SOURCE_TYPE_BUSINESS -> ToolSourceType.BUSINESS;
                    case TOOL_SOURCE_TYPE_MCP -> ToolSourceType.MCP;
                    default -> throw new IllegalArgumentException("Unsupported Tool source type.");
                });
                definition.setDescription(source.getDescription());
                definition.setParametersJson(source.getParametersJson());
                definition.setStrict(source.getStrict());
                definition.setDefinitionHash(source.getDefinitionHash());
                definitions.add(definition);
            }
        }
        if (!definitions.isEmpty())
            requireAffectedRows("Tool definition", definitions.size(),
                conversationLlmToolDefinitionMapper.insertToolDefinitions(definitions));
    }

    /**
     * Batch-persists request messages, then maps Tool calls back to generated message IDs using
     * the logical {@code (llmCallId,messageOrder)} key rather than database return order.
     *
     * @param contexts source/projection pairs for the Round's Turns
     * @param userId authenticated owner written to audit columns
     */
    private void persistRequestMessagesAndToolCalls(List<TurnPersistenceContext> contexts, long userId)
    {
        List<ConversationLlmRequestMessage> messages = new ArrayList<>();
        Map<RequestMessageKey, LlmConversationMessage> sourceMessagesByKey = new HashMap<>();
        for (TurnPersistenceContext context : contexts)
        {
            int messageOrder = 0;
            for (LlmConversationMessage sourceMessage :
                context.sourceTurn().getLlmCall().getRequest().getMessagesList())
            {
                messages.add(toRequestMessage(
                    sourceMessage, context.llmCall().getId(), messageOrder, userId));
                sourceMessagesByKey.put(
                    new RequestMessageKey(context.llmCall().getId(), messageOrder), sourceMessage);
                messageOrder++;
            }
        }

        List<ConversationLlmRequestMessage> savedMessages =
            conversationLlmRequestMessageMapper.insertRequestMessages(messages);
        requireReturnedRows("LLM request message", messages.size(), savedMessages);

        List<ConversationLlmRequestMessageToolCall> requestToolCalls = new ArrayList<>();
        for (ConversationLlmRequestMessage savedMessage : savedMessages)
        {
            RequestMessageKey key = new RequestMessageKey(
                savedMessage.getLlmCallId(), savedMessage.getMessageOrder());
            LlmConversationMessage sourceMessage = sourceMessagesByKey.get(key);
            if (sourceMessage == null)
                throw new IllegalStateException("Request message batch returned an unknown logical row.");
            int callOrder = 0;
            for (ToolCall sourceToolCall : sourceMessage.getToolCallsList())
            {
                ConversationLlmRequestMessageToolCall toolCall =
                    new ConversationLlmRequestMessageToolCall();
                applyAudit(toolCall, userId);
                toolCall.setRequestMessageId(savedMessage.getId());
                toolCall.setCallOrder(callOrder++);
                toolCall.setToolCallId(sourceToolCall.getId());
                toolCall.setType(sourceToolCall.getType());
                toolCall.setFunctionName(sourceToolCall.getFunction().getName());
                toolCall.setArguments(sourceToolCall.getFunction().getArguments());
                requestToolCalls.add(toolCall);
            }
        }
        if (!requestToolCalls.isEmpty())
            requireAffectedRows("Request message Tool call", requestToolCalls.size(),
                conversationLlmRequestMessageToolCallMapper.insertRequestMessageToolCalls(requestToolCalls));
    }

    /**
     * Batch-persists response Tool calls and their execution audit records. If a model response has
     * no Tool calls this stage is intentionally a no-op, keeping ordinary text Rounds cheap.
     *
     * @param contexts source/projection pairs for the Round's Turns
     * @param userId authenticated owner written to audit columns
     */
    private void persistResponseToolCallsAndExecutions(
        List<TurnPersistenceContext> contexts, long userId)
    {
        List<ConversationLlmResponseToolCall> responseToolCalls = new ArrayList<>();
        Map<ResponseToolCallKey, ToolCallExecution> sourceExecutionsByKey = new HashMap<>();
        for (TurnPersistenceContext context : contexts)
        {
            for (ToolCallExecution execution : context.sourceTurn().getToolCallExecutionsList())
                sourceExecutionsByKey.put(
                    new ResponseToolCallKey(context.llmCall().getId(), execution.getToolCallId()), execution);

            int callOrder = 0;
            for (ToolCall sourceToolCall :
                context.sourceTurn().getLlmCall().getResponse().getMessage().getToolCallsList())
            {
                ConversationLlmResponseToolCall responseToolCall = new ConversationLlmResponseToolCall();
                applyAudit(responseToolCall, userId);
                responseToolCall.setTurnId(context.turn().getId());
                responseToolCall.setLlmCallId(context.llmCall().getId());
                responseToolCall.setCallOrder(callOrder++);
                responseToolCall.setToolCallId(sourceToolCall.getId());
                responseToolCall.setType(sourceToolCall.getType());
                responseToolCall.setFunctionName(sourceToolCall.getFunction().getName());
                responseToolCall.setArguments(sourceToolCall.getFunction().getArguments());
                responseToolCalls.add(responseToolCall);
            }
        }

        if (responseToolCalls.isEmpty())
            return;
        List<ConversationLlmResponseToolCall> savedToolCalls =
            conversationLlmResponseToolCallMapper.insertResponseToolCalls(responseToolCalls);
        requireReturnedRows("Response Tool call", responseToolCalls.size(), savedToolCalls);

        List<ConversationToolCallExecution> executions = new ArrayList<>();
        for (ConversationLlmResponseToolCall savedToolCall : savedToolCalls)
        {
            ResponseToolCallKey key = new ResponseToolCallKey(
                savedToolCall.getLlmCallId(), savedToolCall.getToolCallId());
            ToolCallExecution sourceExecution = sourceExecutionsByKey.get(key);
            if (sourceExecution == null)
                throw new IllegalStateException("Response Tool call batch returned an unknown logical row.");
            executions.add(toToolCallExecution(
                sourceExecution, savedToolCall.getTurnId(), savedToolCall.getId(),
                savedToolCall.getCallOrder(), userId));
        }
        requireAffectedRows("Tool execution", executions.size(),
            conversationToolCallExecutionMapper.insertToolCallExecutions(executions));
    }

    /**
     * Serializes stable content parts as JSONB-compatible text for PostgreSQL storage. Signed OSS
     * URLs never reach this method; only stable AgentBreaker references may be persisted.
     *
     * @param contentParts structured text/image/file parts
     * @return JSON text, or {@code null} when the message is scalar/empty
     * @throws IllegalArgumentException when the configured ObjectMapper cannot serialize a part
     */
    private String serializeContentParts(List<ContentPart> contentParts)
    {
        if (contentParts == null || contentParts.isEmpty())
            return null;
        List<Map<String, Object>> values = new ArrayList<>();
        for (ContentPart contentPart : contentParts)
        {
            Map<String, Object> value = new HashMap<>();
            value.put("type", contentPart.getType());
            if (contentPart.getType().equals("text"))
                value.put("text", contentPart.getText());
            else
            {
                Map<String, Object> fileValue = new HashMap<>();
                fileValue.put("url", contentPart.getFileUrl().getUrl());
                fileValue.put("detail", contentPart.getFileUrl().getDetail());
                value.put("file_url", fileValue);
            }
            values.add(value);
        }
        try
        {
            return objectMapper.writeValueAsString(values);
        }
        catch (JsonProcessingException e)
        {
            throw new IllegalArgumentException("Content parts could not be serialized.", e);
        }
    }

    /**
     * Reads persisted content-part JSON defensively for replay and browser projections. Invalid
     * stored JSON is treated as an integrity error instead of silently dropping user content.
     *
     * @param json JSON produced by {@link #serializeContentParts(List)}
     * @return decoded content parts, or an empty list for a null/blank column
     * @throws IllegalStateException when stored JSON is malformed
     */
    private List<ContentPart> deserializeContentParts(String json)
    {
        if (!StringUtils.hasText(json))
            return List.of();
        try
        {
            JsonNode root = objectMapper.readTree(json);
            List<ContentPart> contentParts = new ArrayList<>();
            for (JsonNode item : root)
            {
                String type = item.path("type").asText();
                ContentPart.Builder contentPart = ContentPart.newBuilder().setType(type);
                if (type.equals("text"))
                    contentPart.setText(item.path("text").asText());
                else
                {
                    JsonNode fileValue = item.path("file_url");
                    contentPart.setFileUrl(FileUrl.newBuilder()
                        .setUrl(fileValue.path("url").asText())
                        .setDetail(fileValue.path("detail").asText()));
                }
                contentParts.add(contentPart.build());
            }
            return contentParts;
        }
        catch (JsonProcessingException e)
        {
            throw new IllegalStateException("Persisted content parts are invalid.", e);
        }
    }

    /**
     * Projects only visible text from a multimodal user request; file URLs are intentionally not
     * shown in browser history or automatic titles.
     *
     * @param request RPC user request, possibly containing text and stable file parts
     * @return visible text joined from text parts, or {@code null} for attachment-only input
     */
    private String extractTextContent(UserRequest request)
    {
        if (request == null)
            return null;
        if (StringUtils.hasText(request.getContent()))
            return request.getContent();
        String text = request.getContentPartsList().stream()
            .filter(part -> "text".equals(part.getType()))
            .map(ContentPart::getText)
            .filter(StringUtils::hasText)
            .collect(Collectors.joining("\n\n"));
        return StringUtils.hasText(text) ? text : null;
    }

    /**
     * Projects a persisted Round into the text shown in the browser's user message bubble,
     * recovering legacy JSON parts when the scalar compatibility column is empty.
     *
     * @param round persisted Round entity
     * @return visible user text, or {@code null} for attachment-only input
     */
    private String extractTextContent(ConversationRound round)
    {
        if (round == null)
            return null;
        if (StringUtils.hasText(round.getUserRequestContent()))
            return round.getUserRequestContent();
        String text = deserializeContentParts(round.getUserRequestContentParts()).stream()
            .filter(part -> "text".equals(part.getType()))
            .map(ContentPart::getText)
            .filter(StringUtils::hasText)
            .collect(Collectors.joining("\n\n"));
        return StringUtils.hasText(text) ? text : null;
    }

    /**
     * Rebuilds the user request shape used by the RPC history summary.
     *
     * @param round persisted Round entity
     * @return scalar or structured user request, preserving the mutually exclusive content shape
     */
    UserRequest toProtoUserRequest(ConversationRound round)
    {
        UserRequest.Builder userRequest = UserRequest.newBuilder();
        if (StringUtils.hasText(round.getUserRequestContent()))
            userRequest.setContent(round.getUserRequestContent());
        else
            userRequest.addAllContentParts(deserializeContentParts(round.getUserRequestContentParts()));
        return userRequest.build();
    }

    /**
     * Maps one RPC Tool execution into its normalized persistence entity while attaching the
     * generated response-tool-call ID needed for audit joins.
     *
     * @param source RPC execution evidence
     * @param turnId parent Turn ID
     * @param responseToolCallId generated response Tool call ID
     * @param executionOrder order in which the Tool was executed
     * @param userId authenticated owner written to audit columns
     * @return durable Tool execution entity
     */
    private ConversationToolCallExecution toToolCallExecution(
        ToolCallExecution source,
        long turnId,
        long responseToolCallId,
        int executionOrder,
        long userId)
    {
        ConversationToolCallExecution execution = new ConversationToolCallExecution();
        applyAudit(execution, userId);
        execution.setTurnId(turnId);
        execution.setResponseToolCallId(responseToolCallId);
        execution.setExecutionOrder(executionOrder);
        execution.setToolKey(source.getToolKey());
        execution.setStatus(switch (source.getStatus())
        {
            case TOOL_CALL_EXECUTION_STATUS_COMPLETED -> ToolCallExecutionStatus.COMPLETED;
            case TOOL_CALL_EXECUTION_STATUS_FAILED -> ToolCallExecutionStatus.FAILED;
            case TOOL_CALL_EXECUTION_STATUS_CANCELLED -> ToolCallExecutionStatus.CANCELLED;
            default -> throw new IllegalArgumentException("Unsupported Tool execution status.");
        });
        execution.setResultContent(source.getResultContent().isEmpty() ? null : source.getResultContent());
        execution.setRawResult(source.hasRawResult() ? source.getRawResult() : null);
        execution.setErrorMessage(source.getErrorMessage());
        execution.setStartTime(new Date(source.getStartTime()));
        execution.setEndTime(new Date(source.getEndTime()));
        return execution;
    }

    /**
     * Applies the authenticated owner to both audit columns so child rows cannot be attributed to
     * the service account or to an untrusted ID embedded in a nested message.
     *
     * @param entityBase new entity receiving audit values
     * @param userId authenticated owner
     */
    private void applyAudit(EntityBase entityBase, long userId)
    {
        entityBase.setCreatorId(userId);
        entityBase.setModifierId(userId);
    }

    /**
     * Creates the domain exception used to return a stable RPC validation error without exposing
     * database implementation details to Runner.
     *
     * @param conversationErrorCode public protocol error code
     * @param message client-safe explanation
     * @return domain exception carrying the protocol code
     */
    private RoundPersistenceException error(ConversationErrorCode conversationErrorCode, String message)
    {
        return new RoundPersistenceException(conversationErrorCode.getNumber(), message);
    }

    /**
     * Fails the transaction when a batch insert does not return every expected row; partial child
     * persistence would make replay incomplete even if the parent Round exists.
     *
     * @param label child table name used in the diagnostic
     * @param expected number of inserted source rows
     * @param rows rows returned by the batch INSERT ... RETURNING
     */
    private void requireReturnedRows(String label, int expected, List<?> rows)
    {
        if (rows == null || rows.size() != expected)
            throw new IllegalStateException(label + " batch returned an unexpected row count.");
    }

    /**
     * Fails the transaction when a set-based write affects fewer rows than requested, preserving
     * the all-or-nothing Round invariant.
     *
     * @param label write operation name used in the diagnostic
     * @param expected number of rows that should have changed
     * @param affectedRows database update count
     */
    private void requireAffectedRows(String label, int expected, int affectedRows)
    {
        if (affectedRows != expected)
            throw new IllegalStateException(label + " batch inserted an unexpected row count.");
    }

    private record TurnPersistenceContext(
        ifl.agentbreaker.conversationmanager.rpc.ConversationTurn sourceTurn,
        ConversationTurn turn,
        ConversationLlmCall llmCall)
    {
    }

    private record RequestMessageKey(long llmCallId, int messageOrder)
    {
    }

    private record ResponseToolCallKey(long llmCallId, String toolCallId)
    {
    }
}
