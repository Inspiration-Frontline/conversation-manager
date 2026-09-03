package ifl.agentbreaker.conversationmanager.services.rounds;

import com.fasterxml.jackson.databind.JsonNode;
import ifl.agentbreaker.authcenter.session.UserContextService;
import ifl.agentbreaker.conversationmanager.config.ConversationReferenceProperties;
import ifl.agentbreaker.conversationmanager.dao.ConversationRoundFileMapper;
import ifl.agentbreaker.conversationmanager.dao.FileCleanupTaskMapper;
import ifl.agentbreaker.conversationmanager.dao.FileResourceMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationLlmRequestMessageMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationLlmRequestMessageToolCallMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationLlmToolDefinitionMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationGroupMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationRoundMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationRoundReferenceMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationTurnMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationToolCallExecutionMapper;
import ifl.agentbreaker.conversationmanager.domain.constants.ConversationRoundStatus;
import ifl.agentbreaker.conversationmanager.domain.constants.ConversationFileStatus;
import ifl.agentbreaker.conversationmanager.domain.constants.ConversationTurnStatus;
import ifl.agentbreaker.conversationmanager.domain.constants.LlmMessageRole;
import ifl.agentbreaker.conversationmanager.domain.constants.LlmMessageStorageMode;
import ifl.agentbreaker.conversationmanager.domain.constants.ToolCallExecutionStatus;
import ifl.agentbreaker.conversationmanager.domain.constants.ToolCallType;
import ifl.agentbreaker.conversationmanager.domain.constants.ToolSourceType;
import ifl.agentbreaker.conversationmanager.domain.dtos.ConversationReferenceBoundary;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.ResolveConversationReferencesRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.ConversationReplayResult;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.ConversationRoundHistoryResult;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.ResolvedConversationReference;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.RoundDeletionFailure;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.RoundDeletionResult;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.RoundHistoryView;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.RoundFileHistory;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.RoundAssistantAnswerHistory;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.RoundToolActivityHistory;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.SharedRoundHistoryView;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.Conversation;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationLlmRequestMessage;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationLlmRequestMessageToolCall;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationLlmToolDefinition;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationRound;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationRoundReference;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationTurn;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationToolCallExecution;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.EntityBase;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.FileResource;
import ifl.agentbreaker.conversationmanager.support.ConversationTitleManager;
import ifl.agentbreaker.conversationmanager.support.JsonSerializer;
import ifl.agentbreaker.conversationmanager.rpc.ConversationErrorCode;
import ifl.agentbreaker.conversationmanager.rpc.ContentPart;
import ifl.agentbreaker.conversationmanager.rpc.ConversationReference;
import ifl.agentbreaker.conversationmanager.rpc.FileUrl;
import ifl.agentbreaker.conversationmanager.rpc.FunctionCall;
import ifl.agentbreaker.conversationmanager.rpc.MessageRole;
import ifl.agentbreaker.conversationmanager.rpc.PreparedConversationReference;
import ifl.agentbreaker.conversationmanager.rpc.RoundStatus;
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
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import stark.dataworks.boot.autoconfig.web.LogArgumentsAndResponse;
import stark.dataworks.boot.web.ServiceResponse;

import java.util.ArrayList;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    /** Persistence operations for owned Conversation metadata. */
    @Autowired
    private ConversationMapper conversationMapper;

    /** Persistence operations for Conversation Group membership. */
    @Autowired
    private ConversationGroupMapper conversationGroupMapper;

    /** Persistence operations for Round rows and projections. */
    @Autowired
    private ConversationRoundMapper conversationRoundMapper;

    /** Persistence operations for frozen Conversation references. */
    @Autowired
    private ConversationRoundReferenceMapper conversationRoundReferenceMapper;

    /** Persistence operations for normalized Turn rows. */
    @Autowired
    private ConversationTurnMapper conversationTurnMapper;

    /** Serializer for read-optimized request-message JSONB snapshots. */
    @Autowired
    private ConversationRequestSnapshotSerializer conversationRequestSnapshotSerializer;

    /** Persistence operations for normalized LLM request messages. */
    @Autowired
    private ConversationLlmRequestMessageMapper conversationLlmRequestMessageMapper;

    /** Persistence operations for request-message Tool calls. */
    @Autowired
    private ConversationLlmRequestMessageToolCallMapper conversationLlmRequestMessageToolCallMapper;

    /** Persistence operations for Tool definitions captured on a Turn. */
    @Autowired
    private ConversationLlmToolDefinitionMapper conversationLlmToolDefinitionMapper;

    /** Persistence operations for executed Tool-call evidence. */
    @Autowired
    private ConversationToolCallExecutionMapper conversationToolCallExecutionMapper;

    /** Persistence operations for Round-to-file references. */
    @Autowired
    private ConversationRoundFileMapper conversationRoundFileMapper;

    /** Persistence operations for file-resource ownership and references. */
    @Autowired
    private FileResourceMapper fileResourceMapper;

    /** Persistence operations for deferred file cleanup. */
    @Autowired
    private FileCleanupTaskMapper fileCleanupTaskMapper;

    /** Validates Round requests and lifecycle invariants. */
    @Autowired
    private ConversationRoundValidator conversationRoundValidator;

    /** Configured limits for same-Group Conversation references. */
    @Autowired
    private ConversationReferenceProperties conversationReferenceProperties;

    /** Computes canonical hashes for idempotent Round retries. */
    @Autowired
    private ConversationRoundPayloadHasher conversationRoundPayloadHasher;

    /** Shared serializer for JSONB request and content projections. */
    @Autowired
    private JsonSerializer jsonSerializer;

    /** Per-Conversation lock guarding ordered Round mutations. */
    @Autowired
    private ConversationMutationLock conversationMutationLock;

    /** Programmatic transaction boundary for multi-table Round writes. */
    @Autowired
    private TransactionTemplate transactionTemplate;

    /** Client-visible code for an unknown or unauthorized Conversation. */
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
     * Logically deletes one validated active Round suffix without reducing the high-water mark.
     * The aggregate lock and one database transaction make retry preparation indivisible from
     * other Conversation mutations; Runner invokes this internal operation before model work.
     *
     * @param userId authenticated Conversation owner
     * @param conversationId stable Conversation identifier
     * @param roundNumbers positive unique Round numbers forming the active tail
     * @return deleted Round numbers in descending order, or typed failure details
     */
    public RoundDeletionResult deleteRounds(long userId, String conversationId, List<Long> roundNumbers)
    {
        List<Long> requestedRoundNumbers = validateRoundDeletionRequest(userId, conversationId, roundNumbers);

        try (ConversationMutationLock.LockHandle ignored = conversationMutationLock.acquire(conversationId))
        {
            RoundDeletionResult result = transactionTemplate.execute(status ->
                deleteRoundsInTransaction(userId, conversationId, requestedRoundNumbers, status));
            if (result == null)
                throw new IllegalStateException("Round retry preparation returned no transaction result.");

            return result;
        }
    }

    /**
     * Validates retry deletion input before acquiring the Conversation mutation lock.
     *
     * @param userId authenticated Conversation owner
     * @param conversationId stable Conversation identifier
     * @param roundNumbers requested Round numbers
     * @return ascending immutable Round numbers
     */
    private List<Long> validateRoundDeletionRequest(
        long userId, String conversationId, List<Long> roundNumbers)
    {
        if (userId <= 0 || !StringUtils.hasText(conversationId) || roundNumbers == null || roundNumbers.isEmpty()
            || roundNumbers.stream().anyMatch(roundNumber -> roundNumber == null || roundNumber <= 0)
            || new HashSet<>(roundNumbers).size() != roundNumbers.size())
            throw error(
                ConversationErrorCode.CONVERSATION_ERROR_CODE_INVALID_REQUEST,
                "Round retry requires positive unique Round numbers.");

        return roundNumbers.stream().sorted().toList();
    }

    /**
     * Locks the parent, validates the complete active suffix, and tombstones it as one transaction.
     *
     * @param userId authenticated Conversation owner
     * @param conversationId stable Conversation identifier
     * @param requestedRoundNumbers ascending requested Round numbers
     * @param transactionStatus current transaction used to roll back an incomplete set update
     * @return deleted Round numbers or failure details
     */
    private RoundDeletionResult deleteRoundsInTransaction(
        long userId,
        String conversationId,
        List<Long> requestedRoundNumbers,
        TransactionStatus transactionStatus)
    {
        Conversation conversation = conversationMapper.lockConversationByIdAndUser(conversationId, userId);
        if (conversation == null)
            throw error(
                ConversationErrorCode.CONVERSATION_ERROR_CODE_CONVERSATION_NOT_FOUND,
                "Conversation does not exist.");

        List<Long> activeRoundNumbers = conversationRoundMapper.listActiveRoundNumbers(conversationId);
        validateActiveRoundSuffix(requestedRoundNumbers, activeRoundNumbers);

        List<Long> descendingRoundNumbers = new ArrayList<>(requestedRoundNumbers);
        descendingRoundNumbers.sort(Collections.reverseOrder());
        int deletedCount = conversationRoundMapper.tombstoneRounds(
            conversationId, descendingRoundNumbers, userId);
        if (deletedCount == descendingRoundNumbers.size())
            return new RoundDeletionResult(List.copyOf(descendingRoundNumbers), List.of());

        transactionStatus.setRollbackOnly();
        List<RoundDeletionFailure> failures = descendingRoundNumbers.stream()
            .map(roundNumber -> new RoundDeletionFailure(
                roundNumber,
                ConversationErrorCode.CONVERSATION_ERROR_CODE_ROUND_NOT_FOUND_VALUE,
                "Round was not deleted."))
            .toList();
        return new RoundDeletionResult(List.of(), failures);
    }

    /**
     * Requires the requested values to be the exact contiguous suffix of active Round numbers.
     *
     * @param requestedRoundNumbers ascending requested Round numbers
     * @param activeRoundNumbers ascending active Round numbers
     */
    private void validateActiveRoundSuffix(List<Long> requestedRoundNumbers, List<Long> activeRoundNumbers)
    {
        int startIndex = activeRoundNumbers.size() - requestedRoundNumbers.size();
        if (startIndex < 0
            || !activeRoundNumbers.subList(startIndex, activeRoundNumbers.size()).equals(requestedRoundNumbers))
            throw error(
                ConversationErrorCode.CONVERSATION_ERROR_CODE_DELETE_REQUIRES_TAIL_SUFFIX,
                "Round retry requires a contiguous suffix ending at the latest active Round.");
    }

    /**
     * Builds the browser history view from compact Round rows plus batched Tool, attachment, and
     * reference projections. Text is recovered from stored content parts for rows written before
     * the scalar compatibility column was populated, allowing old conversations to remain fully
     * readable after refresh.
     *
     * @param userId authenticated browser identity
     * @param conversationId Conversation selected in the UI
     * @return service envelope containing visible messages and replayable activity summaries
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
            Map<Long, List<RoundToolActivityHistory>> toolActivitiesByRound = conversationToolCallExecutionMapper
                .listRoundToolActivities(conversationId)
                .stream()
                .collect(Collectors.groupingBy(RoundToolActivityHistory::roundNumber));
            Map<Long, List<ConversationRoundReference>> referencesByRound = listReferencesByRound(history.rounds());
            Map<Long, String> assistantAnswersByRound = conversationTurnMapper
                .listLatestRoundAnswers(conversationId)
                .stream()
                .collect(Collectors.toMap(
                    RoundAssistantAnswerHistory::roundNumber,
                    RoundAssistantAnswerHistory::assistantAnswer));
            return ServiceResponse.buildSuccessResponse(new RoundHistoryView(
                conversationId,
                history.latestRoundNumber(),
                history.rounds().stream()
                    .map(round -> toRoundView(
                        round, toolActivitiesByRound, filesByRound, referencesByRound, assistantAnswersByRound))
                    .toList()));
        }
        catch (RoundPersistenceException e)
        {
            return ServiceResponse.buildErrorResponse(e.getCode(), e.getMessage());
        }
    }

    /** Builds one Round history view from its grouped Tool, file, and reference projections.
     * @param round persisted Round metadata
     * @param toolActivitiesByRound Tool evidence grouped by Round number
     * @param filesByRound file evidence grouped by Round number
     * @param referencesByRound frozen references grouped by database Round ID
     * @return user-visible Round history view
     */
    private RoundHistoryView.RoundView toRoundView(
        ConversationRound round,
        Map<Long, List<RoundToolActivityHistory>> toolActivitiesByRound,
        Map<Long, List<RoundFileHistory>> filesByRound,
        Map<Long, List<ConversationRoundReference>> referencesByRound,
        Map<Long, String> assistantAnswersByRound)
    {
        return new RoundHistoryView.RoundView(
            round.getRoundNumber(), extractTextContent(round),
            round.getFinalAnswerContent() == null
                ? assistantAnswersByRound.get(round.getRoundNumber())
                : round.getFinalAnswerContent(),
            round.getStatus().name(), round.getErrorMessage(), round.getTurnCount(),
            round.getStartTime().toEpochMilli(), getRoundEndTime(round),
            toolActivitiesByRound.getOrDefault(round.getRoundNumber(), List.of()).stream()
                .map(this::toToolActivityView)
                .toList(),
            filesByRound.getOrDefault(round.getRoundNumber(), List.of()).stream()
                .map(file -> new RoundHistoryView.FileView(
                    file.fileId(), file.originalFilename(), file.mimeType(), file.fileSize(),
                    file.kind(), file.status()))
                .toList(),
            referencesByRound.getOrDefault(round.getId(), List.of()).stream()
                .map(this::toReferenceView)
                .toList());
    }

    /** Converts persisted Tool activity into the HTTP history projection.
     * @param activity persisted Tool activity row
     * @return user-visible Tool activity view
     */
    private RoundHistoryView.ToolActivityView toToolActivityView(RoundToolActivityHistory activity)
    {
        return new RoundHistoryView.ToolActivityView(
            activity.toolCallId(), activity.toolName(), activity.toolKey(), activity.arguments(),
            activity.status(), activity.resultContent(), activity.errorMessage());
    }

    /** Resolves the terminal timestamp while tolerating an in-progress Round.
     * @param round persisted Round row
     * @return end time in epoch milliseconds, or start time when not finished
     */
    private long getRoundEndTime(ConversationRound round)
    {
        return round.getEndTime() == null ? round.getStartTime().toEpochMilli() : round.getEndTime().toEpochMilli();
    }

    /**
     * Freezes the current high-water boundary and title for every selected source in one request.
     * The browser uses this lightweight projection before streaming, while Runner preparation
     * still revalidates the same ownership, Group, and boundary invariants at execution time.
     *
     * @param request destination scope and ordered source Conversation identifiers
     * @return ordered title and boundary snapshots, or a client-safe validation error
     */
    public ServiceResponse<List<ResolvedConversationReference>> resolveConversationReferences(
        ResolveConversationReferencesRequest request)
    {
        long userId = UserContextService.getCurrentUserId();
        try
        {
            return ServiceResponse.buildSuccessResponse(resolveReferenceBoundaries(userId, request));
        }
        catch (RoundPersistenceException e)
        {
            return ServiceResponse.buildErrorResponse(e.getCode(), e.getMessage());
        }
    }

    /** Resolves and validates source Conversation boundaries for the HTTP picker.
     * @param userId trusted authenticated caller identity
     * @param request destination and ordered source selection
     * @return ordered title and high-water snapshots
     */
    private List<ResolvedConversationReference> resolveReferenceBoundaries(
        long userId, ResolveConversationReferencesRequest request)
    {
        List<String> orderedSourceIds = validateReferenceResolutionRequest(userId, request);
        long groupId = resolveReferenceGroupId(userId, request, orderedSourceIds);
        Set<String> sourceIds = new LinkedHashSet<>(orderedSourceIds);
        Map<String, Conversation> sourcesById = loadReferenceSources(userId, sourceIds);
        validateResolvedSources(groupId, sourceIds, sourcesById);

        Set<String> sourcesWithCompletedRounds = new HashSet<>(
            conversationRoundMapper.listConversationIdsWithCompletedRounds(sourceIds));
        if (sourcesWithCompletedRounds.size() != sourceIds.size())
            throw invalidReferenceRequest(
                "Every referenced Conversation must contain an active completed Round.");

        return orderedSourceIds.stream()
            .map(sourceId -> toResolvedReference(sourcesById.get(sourceId)))
            .toList();
    }

    /**
     * Validates the shape and mutually exclusive destination fields of a reference-resolution
     * request while preserving the caller's source ordering.
     *
     * @param userId authenticated caller identity that must be positive
     * @param request destination selector and ordered source Conversation IDs
     * @return immutable ordered source IDs after blank and duplicate validation
     * @throws RoundPersistenceException when the request shape or reference count is invalid
     */
    private List<String> validateReferenceResolutionRequest(
        long userId, ResolveConversationReferencesRequest request)
    {
        if (userId <= 0 || request == null || request.getSourceConversationIds() == null
            || request.getSourceConversationIds().isEmpty()
            || request.getSourceConversationIds().size() > conversationReferenceProperties.getMaxCountPerRound())
            throw invalidReferenceRequest("The Conversation reference request is invalid.");

        long requestedGroupId = request.getConversationGroupId();
        if (requestedGroupId < 0)
            throw invalidReferenceRequest("Conversation Group ID must be positive.");

        boolean hasDestination = StringUtils.hasText(request.getDestinationConversationId());
        boolean hasGroup = requestedGroupId > 0;
        if (hasDestination == hasGroup)
            throw invalidReferenceRequest(
                "Exactly one destination Conversation or Conversation Group is required.");

        List<String> sourceIds = request.getSourceConversationIds();
        Set<String> uniqueSourceIds = new LinkedHashSet<>();
        for (String sourceId : sourceIds)
        {
            if (!StringUtils.hasText(sourceId) || !uniqueSourceIds.add(sourceId))
                throw invalidReferenceRequest(
                    "Referenced Conversations must be non-empty and unique.");
        }
        return List.copyOf(sourceIds);
    }

    /** Determines the Group that authorizes the selected source Conversations.
     * @param userId trusted authenticated caller identity
     * @param request destination Conversation or Group selection
     * @param sourceIds normalized source Conversation identifiers
     * @return authorized Group database identity
     */
    private long resolveReferenceGroupId(
        long userId, ResolveConversationReferencesRequest request, List<String> sourceIds)
    {
        if (StringUtils.hasText(request.getDestinationConversationId()))
        {
            Conversation destination = getReferenceDestination(userId, request.getDestinationConversationId());
            if (sourceIds.contains(destination.getConversationId()))
                throw invalidReferenceRequest("A Conversation cannot reference itself.");
            return destination.getConversationGroupId();
        }

        long groupId = request.getConversationGroupId();
        if (!conversationGroupMapper.existsByIdAndUser(groupId, userId))
            throw invalidReferenceRequest("Conversation Group does not exist.");
        return groupId;
    }

    /** Verifies that every selected source is owned by and belongs to the resolved Group.
     * @param groupId authorized Group database identity
     * @param sourceIds normalized source Conversation identifiers
     * @param sourcesById loaded source Conversations keyed by public ID
     */
    private void validateResolvedSources(
        long groupId, Set<String> sourceIds, Map<String, Conversation> sourcesById)
    {
        if (sourcesById.size() != sourceIds.size())
            throw invalidReferenceRequest(
                "Every referenced Conversation must belong to the current user and Group.");

        for (Conversation source : sourcesById.values())
        {
            if (!Objects.equals(groupId, source.getConversationGroupId()))
                throw invalidReferenceRequest(
                    "Every referenced Conversation must belong to the current user and Group.");
        }
    }

    /** Converts an owned Conversation into a frozen reference summary.
     * @param source owned source Conversation
     * @return source title and current high-water boundary
     */
    private ResolvedConversationReference toResolvedReference(Conversation source)
    {
        return new ResolvedConversationReference(
            source.getConversationId(), source.getTitle(), source.getLatestRoundNumber());
    }

    /** Creates the protocol error used for invalid reference selections.
     * @param message caller-safe validation message
     * @return classified invalid-reference exception
     */
    private RoundPersistenceException invalidReferenceRequest(String message)
    {
        return new RoundPersistenceException(
            ConversationErrorCode.CONVERSATION_ERROR_CODE_INVALID_REQUEST_VALUE, message);
    }

    /**
     * Builds the read-only snapshot projection for a valid share. Ownership is intentionally not
     * checked here; the caller must have already validated the share record and its lifecycle.
     * Only active completed Rounds at or below the frozen boundary are exposed.
     *
     * @param conversationId source Conversation identifier
     * @param endRoundNumber inclusive snapshot boundary
     * @return completed Round history visible through the share
     */
    public SharedRoundHistoryView getSharedHttpHistory(String conversationId, long endRoundNumber)
    {
        Map<Long, List<RoundFileHistory>> filesByRound = conversationRoundFileMapper
            .listCompletedRoundFilesAtOrBefore(conversationId, endRoundNumber)
            .stream()
            .collect(Collectors.groupingBy(RoundFileHistory::roundNumber));
        List<ConversationRound> visibleRounds = conversationRoundMapper
            .listCompletedRoundsAtOrBefore(conversationId, endRoundNumber);
        Map<Long, List<ConversationRoundReference>> referencesByRound = listReferencesByRound(visibleRounds);

        long latestRoundNumber = visibleRounds.isEmpty()
            ? 0
            : visibleRounds.getLast().getRoundNumber();

        return new SharedRoundHistoryView(
            latestRoundNumber,
            visibleRounds.stream().map(round -> new SharedRoundHistoryView.RoundView(
                round.getRoundNumber(), extractTextContent(round), round.getFinalAnswerContent(),
                round.getStatus().name(), round.getErrorMessage(), round.getTurnCount(),
                round.getStartTime().toEpochMilli(), round.getEndTime().toEpochMilli(),
                filesByRound.getOrDefault(round.getRoundNumber(), List.of()).stream()
                    .map(file -> new SharedRoundHistoryView.FileView(
                        file.fileId(), file.originalFilename(), file.mimeType(), file.fileSize(),
                        file.kind(), file.status()))
                .toList(),
                referencesByRound.getOrDefault(round.getId(), List.of()).stream()
                    .map(this::toSharedReferenceView)
                    .toList())).toList());
    }

    /** Loads and groups frozen references for the supplied Round rows.
     * @param rounds persisted Rounds whose references are needed
     * @return references grouped by database Round ID
     */
    private Map<Long, List<ConversationRoundReference>> listReferencesByRound(List<ConversationRound> rounds)
    {
        if (rounds.isEmpty())
            return Map.of();
        List<Long> roundIds = rounds.stream().map(ConversationRound::getId).toList();
        return conversationRoundReferenceMapper.listReferencesByRoundIds(roundIds).stream()
            .collect(Collectors.groupingBy(ConversationRoundReference::getRoundId));
    }

    /** Converts a stored reference to the owner-visible history view.
     * @param reference persisted frozen reference
     * @return reference view including source ID and boundary
     */
    private RoundHistoryView.ReferenceView toReferenceView(ConversationRoundReference reference)
    {
        return new RoundHistoryView.ReferenceView(
            reference.getSourceConversationId(),
            reference.getSourceEndRoundNumber(),
            reference.getSourceTitle());
    }

    /** Converts a stored reference to the redacted sharing projection.
     * @param reference persisted frozen reference
     * @return shared view excluding the source Conversation ID
     */
    private SharedRoundHistoryView.ReferenceView toSharedReferenceView(
        ConversationRoundReference reference)
    {
        return new SharedRoundHistoryView.ReferenceView(
            reference.getSourceEndRoundNumber(), reference.getSourceTitle());
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

        ReplayTurnBoundary replayBoundary = resolveReplayTurnBoundary(
            conversationId, endRoundNumber, boundaryRound);
        if (replayBoundary == null)
            return new ConversationReplayResult(conversationId, List.of());

        Map<Long, List<ConversationLlmRequestMessageToolCall>> toolCallsByMessageId =
            conversationLlmRequestMessageToolCallMapper
                .listRequestMessageToolCallsForRound(replayBoundary.round().getId())
                .stream().collect(Collectors.groupingBy(
                    ConversationLlmRequestMessageToolCall::getRequestMessageId));
        List<LlmConversationMessage> contextMessages = conversationLlmRequestMessageMapper
            .listRequestMessagesForRound(replayBoundary.round().getId()).stream()
            .map(message -> toProtoMessage(message, toolCallsByMessageId.getOrDefault(message.getId(), List.of())))
            .collect(Collectors.toCollection(ArrayList::new));
        contextMessages.add(LlmConversationMessage.newBuilder()
            .setRole(MessageRole.MESSAGE_ROLE_ASSISTANT)
            .setContent(replayBoundary.turn().getResponseContent())
            .build());
        return new ConversationReplayResult(conversationId, List.copyOf(contextMessages));
    }

    /**
     * Selects the latest durable model response eligible for subsequent Conversation context.
     *
     * <p>A canceled boundary with visible model text remains part of the conversation. Failed
     * boundaries and cancellations without a model response fall back to the latest completed
     * Round so provider errors never become prompt history.</p>
     *
     * @param conversationId stable owned Conversation identifier
     * @param endRoundNumber inclusive active replay boundary
     * @param boundaryRound active Round at the requested boundary
     * @return replayable Round and Turn, or {@code null} when no model response exists
     */
    private ReplayTurnBoundary resolveReplayTurnBoundary(
        String conversationId, long endRoundNumber, ConversationRound boundaryRound)
    {
        if (boundaryRound.getStatus() == ConversationRoundStatus.CANCELLED)
        {
            ConversationTurn cancelledTurn = conversationTurnMapper.getLatestTurn(boundaryRound.getId());
            if (cancelledTurn != null
                && cancelledTurn.isResponseMessagePresent()
                && StringUtils.hasText(cancelledTurn.getResponseContent()))
                return new ReplayTurnBoundary(boundaryRound, cancelledTurn);
        }

        ConversationRound completedRound = conversationRoundMapper.getLatestCompletedRoundAtOrBefore(
            conversationId, endRoundNumber);
        if (completedRound == null)
            return null;
        ConversationTurn completedTurn = conversationTurnMapper.getCompletedTurn(
            completedRound.getId(), completedRound.getFinalSourceTurnNumber());
        if (completedTurn == null || !completedTurn.isResponseMessagePresent())
            throw new IllegalStateException("Completed replay Round has no response Turn.");
        return new ReplayTurnBoundary(completedRound, completedTurn);
    }

    /**
     * Authorizes and projects frozen same-Group references without exposing Tool or intermediate
     * Turn traces. The returned messages are derived evidence and never mutate source history.
     *
     * @param userId authenticated owner of destination and source Conversations
     * @param destinationConversationId stable destination Conversation identifier
     * @param references ordered source identifiers and inclusive frozen Round boundaries
     * @return authorized reference evidence in the same order as the request
     */
    public List<PreparedConversationReference> prepareReferences(
        long userId, String destinationConversationId, List<ConversationReference> references)
    {
        validatePrepareReferencesRequest(userId, destinationConversationId, references);

        Conversation destination = getReferenceDestination(userId, destinationConversationId);
        List<ConversationReferenceBoundary> boundaries = buildReferenceBoundaries(
            destinationConversationId, references);
        Set<String> sourceIds = boundaries.stream()
            .map(ConversationReferenceBoundary::sourceConversationId)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, Conversation> sourcesById = loadReferenceSources(userId, sourceIds);
        Map<RoundBoundaryKey, ConversationRound> roundsByBoundary = loadReferenceBoundaryRounds(boundaries);

        validateReferenceBoundaries(destination, boundaries, sourcesById, roundsByBoundary);

        Map<String, List<ConversationRound>> completedRoundsByConversation = loadCompletedReferenceRounds(boundaries);
        validateCompletedReferenceRounds(boundaries, completedRoundsByConversation);
        return buildPreparedReferences(references, sourcesById, completedRoundsByConversation);
    }

    /** Validates the identity, destination, and configured count limit for Runner references.
     * @param userId Trusted authenticated user identifier.
     * @param destinationConversationId Stable identifier of the destination conversation.
     * @param references source Conversation IDs and frozen ending Round numbers to validate
     */
    private void validatePrepareReferencesRequest(
        long userId, String destinationConversationId, List<ConversationReference> references)
    {
        if (userId <= 0 || !StringUtils.hasText(destinationConversationId)
            || references.isEmpty()
            || references.size() > conversationReferenceProperties.getMaxCountPerRound())
            throw new RoundPersistenceException(
                ConversationErrorCode.CONVERSATION_ERROR_CODE_INVALID_REQUEST_VALUE,
                "The Conversation reference request is invalid.");
    }

    /** Loads and validates the destination Conversation for reference preparation.
     * @param userId Trusted authenticated user identifier.
     * @param destinationConversationId Stable identifier of the destination conversation.
     * @return owned destination Conversation used for Group validation
     */
    private Conversation getReferenceDestination(long userId, String destinationConversationId)
    {
        Conversation destination = conversationMapper.getConversationByIdAndUser(destinationConversationId, userId);
        if (destination == null)
            throw new RoundPersistenceException(ERROR_CONVERSATION_NOT_FOUND, "Conversation does not exist.");
        if (destination.getConversationGroupId() == null)
            throw new RoundPersistenceException(
                ConversationErrorCode.CONVERSATION_ERROR_CODE_INVALID_REQUEST_VALUE,
                "Conversation references require a Group.");

        return destination;
    }

    /** Normalizes source selections into unique frozen boundary values.
     * @param destinationConversationId Stable identifier of the destination conversation.
     * @param references source Conversation IDs and ending Round numbers supplied by Runner
     * @return ordered, duplicate-free boundary projections
     */
    private List<ConversationReferenceBoundary> buildReferenceBoundaries(
        String destinationConversationId, List<ConversationReference> references)
    {
        Set<String> sourceIds = new LinkedHashSet<>();
        List<ConversationReferenceBoundary> boundaries = new ArrayList<>();
        for (ConversationReference reference : references)
        {
            String sourceId = reference.getSourceConversationId();
            if (!StringUtils.hasText(sourceId)
                || sourceId.equals(destinationConversationId)
                || !sourceIds.add(sourceId)
                || reference.getSourceEndRoundNumber() <= 0)
                throw new RoundPersistenceException(
                    ConversationErrorCode.CONVERSATION_ERROR_CODE_INVALID_REQUEST_VALUE,
                    "Conversation references must be non-empty, unique, and use a positive boundary.");

            boundaries.add(toBoundary(reference));
        }

        return List.copyOf(boundaries);
    }

    /** Loads all selected source Conversations in one ownership-scoped query.
     * @param userId Trusted authenticated user identifier.
     * @param sourceIds Stable identifiers of the selected source values.
     * @return owned source Conversations keyed by their public IDs
     */
    private Map<String, Conversation> loadReferenceSources(long userId, Set<String> sourceIds)
    {
        return conversationMapper
            .listConversationsByIdsAndUser(sourceIds, userId)
            .stream()
            .collect(Collectors.toMap(Conversation::getConversationId, source -> source));
    }

    /** Loads all selected boundary Rounds in one set-based query.
     * @param boundaries source Conversation and ending Round pairs to load
     * @return persisted boundary Rounds keyed by Conversation and Round number
     */
    private Map<RoundBoundaryKey, ConversationRound> loadReferenceBoundaryRounds(
        List<ConversationReferenceBoundary> boundaries)
    {
        return conversationRoundMapper
            .listRoundsAtBoundaries(boundaries)
            .stream()
            .collect(Collectors.toMap(
                round -> new RoundBoundaryKey(round.getConversationId(), round.getRoundNumber()),
                round -> round));
    }

    /**
     * Verifies that each reference belongs to the destination Group and points to an existing,
     * non-deleted Round no newer than its source Conversation high-water mark.
     *
     * @param destination owned destination Conversation defining the required Group
     * @param boundaries ordered source Conversation and Round boundary pairs
     * @param sourcesById owned source Conversations keyed by public Conversation ID
     * @param roundsByBoundary persisted boundary Rounds keyed by Conversation and Round number
     * @throws RoundPersistenceException when Group membership or a Round boundary is invalid
     */
    private void validateReferenceBoundaries(
        Conversation destination,
        List<ConversationReferenceBoundary> boundaries,
        Map<String, Conversation> sourcesById,
        Map<RoundBoundaryKey, ConversationRound> roundsByBoundary)
    {
        for (ConversationReferenceBoundary boundary : boundaries)
        {
            Conversation source = sourcesById.get(boundary.sourceConversationId());
            if (source == null || !Objects.equals(
                destination.getConversationGroupId(), source.getConversationGroupId()))
                throw new RoundPersistenceException(
                    ConversationErrorCode.CONVERSATION_ERROR_CODE_INVALID_REQUEST_VALUE,
                    "Every referenced Conversation must belong to the current Group.");
            if (boundary.sourceEndRoundNumber() > source.getLatestRoundNumber())
                throw new RoundPersistenceException(
                    ConversationErrorCode.CONVERSATION_ERROR_CODE_INVALID_REQUEST_VALUE,
                    "A referenced Round boundary is newer than the source Conversation.");

            ConversationRound boundaryRound = roundsByBoundary.get(new RoundBoundaryKey(
                boundary.sourceConversationId(), boundary.sourceEndRoundNumber()));
            if (boundaryRound == null || boundaryRound.isDeleted())
                throw new RoundPersistenceException(
                    ConversationErrorCode.CONVERSATION_ERROR_CODE_ROUND_NOT_FOUND_VALUE,
                    "A referenced Round boundary does not exist.");
        }
    }

    /** Loads completed source Rounds up to every frozen boundary.
     * @param boundaries source Conversation and ending Round pairs defining each high-water limit
     * @return completed Rounds grouped by source Conversation ID
     */
    private Map<String, List<ConversationRound>> loadCompletedReferenceRounds(
        List<ConversationReferenceBoundary> boundaries)
    {
        return conversationRoundMapper
            .listCompletedRoundsAtOrBeforeBoundaries(boundaries)
            .stream()
            .collect(Collectors.groupingBy(ConversationRound::getConversationId));
    }

    /** Ensures every selected source contributes at least one completed Round.
     * @param boundaries ordered source Conversation and Round boundary pairs
     * @param completedRoundsByConversation completed Rounds grouped by source Conversation ID
     */
    private void validateCompletedReferenceRounds(
        List<ConversationReferenceBoundary> boundaries,
        Map<String, List<ConversationRound>> completedRoundsByConversation)
    {
        for (ConversationReferenceBoundary boundary : boundaries)
        {
            if (completedRoundsByConversation.getOrDefault(
                boundary.sourceConversationId(), List.of()).isEmpty())
                throw invalidReferenceRequest(
                    "Every referenced Conversation must contain an active completed Round.");
        }
    }

    /**
     * Projects validated source histories into alternating user/assistant context messages while
     * retaining the caller's reference order and frozen boundary metadata.
     *
     * @param references ordered validated references supplied by Runner
     * @param sourcesById source Conversation metadata keyed by public Conversation ID
     * @param completedRoundsByConversation completed source Rounds grouped by Conversation ID
     * @return immutable prepared reference projections ready for the Runner context builder
     */
    private List<PreparedConversationReference> buildPreparedReferences(
        List<ConversationReference> references,
        Map<String, Conversation> sourcesById,
        Map<String, List<ConversationRound>> completedRoundsByConversation)
    {
        List<PreparedConversationReference> prepared = new ArrayList<>();
        for (ConversationReference reference : references)
        {
            Conversation source = sourcesById.get(reference.getSourceConversationId());
            PreparedConversationReference.Builder item = PreparedConversationReference.newBuilder()
                .setReference(reference)
                .setSourceTitle(source.getTitle());

            for (ConversationRound round : completedRoundsByConversation.getOrDefault(
                reference.getSourceConversationId(), List.of()))
            {
                item.addContextMessages(LlmConversationMessage.newBuilder()
                    .setRole(MessageRole.MESSAGE_ROLE_USER)
                    .setContent(extractTextContent(round))
                    .build());
                item.addContextMessages(LlmConversationMessage.newBuilder()
                    .setRole(MessageRole.MESSAGE_ROLE_ASSISTANT)
                    .setContent(round.getFinalAnswerContent() == null ? "" : round.getFinalAnswerContent())
                    .build());
            }

            prepared.add(item.build());
        }

        return List.copyOf(prepared);
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
                .setType(toolCall.getType().getWireValue())
                .setFunction(FunctionCall.newBuilder()
                    .setName(toolCall.getFunctionName())
                    .setArguments(toolCall.getArguments()))
                .build()).toList())
            .setToolCallId(message.getToolCallId() == null ? "" : message.getToolCallId());
        builder.addAllContentParts(deserializeContentParts(message.getContentParts()));
        return builder.build();
    }

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
        conversationRoundValidator.validateRoundRequest(request);
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
        persistRoundReferences(request, conversation, savedRound.getId());
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

    void persistRoundReferences(
        SaveConversationRoundRequest request, Conversation destination, long roundId)
    {
        if (request.getReferencesCount() == 0)
            return;

        List<ConversationReferenceBoundary> boundaries = request.getReferencesList()
            .stream()
            .map(this::toBoundary)
            .toList();
        Set<String> sourceIds = boundaries.stream()
            .map(ConversationReferenceBoundary::sourceConversationId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, Conversation> sourcesById = conversationMapper
            .listConversationsByIdsAndUser(sourceIds, request.getUserId())
            .stream()
            .collect(Collectors.toMap(Conversation::getConversationId, source -> source));
        Map<RoundBoundaryKey, ConversationRound> roundsByBoundary = conversationRoundMapper
            .listRoundsAtBoundaries(boundaries)
            .stream()
            .collect(Collectors.toMap(
                boundary -> new RoundBoundaryKey(boundary.getConversationId(), boundary.getRoundNumber()),
                boundary -> boundary));

        List<ConversationRoundReference> rows = new ArrayList<>();
        int referenceOrder = 0;
        for (ConversationReference reference : request.getReferencesList())
        {
            Conversation source = sourcesById.get(reference.getSourceConversationId());
            if (source == null || !Objects.equals(
                destination.getConversationGroupId(), source.getConversationGroupId())
                || destination.getConversationGroupId() == null)
                throw error(ConversationErrorCode.CONVERSATION_ERROR_CODE_INVALID_REQUEST,
                    "Every referenced Conversation must belong to the destination Group.");

            ConversationRound boundary = roundsByBoundary.get(new RoundBoundaryKey(
                source.getConversationId(), reference.getSourceEndRoundNumber()));
            if (boundary == null || boundary.isDeleted()
                || reference.getSourceEndRoundNumber() > source.getLatestRoundNumber())
                throw error(ConversationErrorCode.CONVERSATION_ERROR_CODE_ROUND_NOT_FOUND,
                    "A referenced Round boundary does not exist.");

            ConversationRoundReference row = new ConversationRoundReference();
            applyAudit(row, request.getUserId());
            row.setRoundId(roundId);
            row.setSourceConversationId(source.getConversationId());
            row.setSourceEndRoundNumber(reference.getSourceEndRoundNumber());
            row.setSourceTitle(source.getTitle());
            row.setReferenceOrder(referenceOrder++);
            rows.add(row);
        }

        requireAffectedRows("Conversation reference", rows.size(),
            conversationRoundReferenceMapper.insertReferences(rows));
    }

    /**
     * Validates stable file references, creates all Round links in one batch, and cancels orphan
     * cleanup tasks. The loop only normalizes IDs; it does not issue one SQL write per file.
     *
     * @param request Round containing stable AgentBreaker file URLs
     * @param roundId newly inserted parent Round ID
     * @return owned file resources used for title fallback and state checks
     */
    List<FileResource> persistRoundFiles(SaveConversationRoundRequest request, long roundId)
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
        fileCleanupTaskMapper.cancelByFileResourceIds(fileResourceIds);
        return fileResources;
    }

    /**
     * Persists Turns and all LLM/Tool child rows using one batch operation per table, then rebuilds
     * associations from returned business keys because PostgreSQL does not promise RETURNING order.
     *
     * @param request complete Runner capture
     * @param roundId parent Round database ID
     */
    void persistTurnsAndChildren(SaveConversationRoundRequest request, long roundId)
    {
        if (request.getTurnsList().isEmpty())
            return;

        List<ConversationTurn> turns = new ArrayList<>();
        for (ifl.agentbreaker.conversationmanager.rpc.ConversationTurn sourceTurn : request.getTurnsList())
            turns.add(toTurn(sourceTurn, roundId, request.getUserId()));
        List<ConversationTurn> savedTurns = conversationTurnMapper.insertTurns(turns);
        requireReturnedRows("Turn", turns.size(), savedTurns);

        List<TurnPersistenceContext> contexts = new ArrayList<>();
        for (ConversationTurn savedTurn : savedTurns)
        {
            int sourceIndex = Math.toIntExact(savedTurn.getTurnNumber() - 1);
            contexts.add(new TurnPersistenceContext(request.getTurns(sourceIndex), savedTurn));
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
        conversationRound.setTraceId(request.getTraceId());
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
        conversationRound.setStartTime(Instant.ofEpochMilli(request.getStartTime()));
        conversationRound.setEndTime(Instant.ofEpochMilli(request.getEndTime()));
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
    private ConversationTurn toTurn(ifl.agentbreaker.conversationmanager.rpc.ConversationTurn source,
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
        conversationTurn.setStartTime(Instant.ofEpochMilli(source.getStartTime()));
        conversationTurn.setEndTime(Instant.ofEpochMilli(source.getEndTime()));
        conversationTurn.setLlmStartTime(Instant.ofEpochMilli(source.getLlmStartTime()));
        conversationTurn.setLlmEndTime(Instant.ofEpochMilli(source.getLlmEndTime()));
        conversationTurn.setRequestId(source.getRequestId());
        conversationTurn.setTraceId(source.getTraceId());
        LlmRequest request = source.getRequest();
        conversationTurn.setMessageStorageMode(switch (request.getMessageStorageMode())
        {
            case LLM_MESSAGE_STORAGE_MODE_FULL_SNAPSHOT -> LlmMessageStorageMode.FULL_SNAPSHOT;
            case LLM_MESSAGE_STORAGE_MODE_APPEND_DELTA -> LlmMessageStorageMode.APPEND_DELTA;
            default -> throw new IllegalArgumentException("Unsupported LLM message storage mode.");
        });
        conversationTurn.setRequestMessagesSnapshot(
            conversationRequestSnapshotSerializer.serialize(request.getMessagesList()));
        conversationTurn.setRawRequest(request.hasRawRequest() ? request.getRawRequest() : null);
        LlmResponse response = source.getResponse();
        conversationTurn.setResponseMessagePresent(response.hasMessage());
        conversationTurn.setResponseContent(response.hasMessage() ? response.getMessage().getContent() : null);
        conversationTurn.setResponseContentParts(
            response.hasMessage() ? serializeContentParts(response.getMessage().getContentPartsList()) : null);
        conversationTurn.setFinishReason(response.getFinishReason());
        conversationTurn.setUsagePresent(response.hasUsage());
        if (response.hasUsage())
        {
            TokenUsage usage = response.getUsage();
            conversationTurn.setPromptTokens(usage.getPromptTokens());
            conversationTurn.setCompletionTokens(usage.getCompletionTokens());
            conversationTurn.setTotalTokens(usage.getTotalTokens());
            conversationTurn.setCachedPromptTokens(usage.getCachedPromptTokens());
            conversationTurn.setReasoningTokens(usage.getReasoningTokens());
        }
        conversationTurn.setRawResponse(response.hasRawResponse() ? response.getRawResponse() : null);
        conversationTurn.setResponseErrorMessage(response.getErrorMessage());
        conversationTurn.setReasoningContent(response.hasReasoningContent() ? response.getReasoningContent() : null);
        return conversationTurn;
    }

    /**
     * Maps a normalized model request message into the durable audit table shape. Scalar content
     * and structured parts remain mutually exclusive, matching the validator's replay contract.
     *
     * @param source normalized RPC message
     * @param roundId parent Round ID
     * @param turnId parent Turn ID
     * @param messageOrder zero-based order within that call
     * @param userId authenticated owner written to audit columns
     * @return durable request-message entity
     */
    private ConversationLlmRequestMessage toRequestMessage(
        LlmConversationMessage source, long roundId, long turnId, int messageOrder, long userId)
    {
        ConversationLlmRequestMessage conversationLlmRequestMessage = new ConversationLlmRequestMessage();
        applyAudit(conversationLlmRequestMessage, userId);
        conversationLlmRequestMessage.setRoundId(roundId);
        conversationLlmRequestMessage.setTurnId(turnId);
        conversationLlmRequestMessage.setMessageOrder(messageOrder);
        conversationLlmRequestMessage.setRole(conversationRequestSnapshotSerializer.mapRole(source.getRole()));
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
            for (ToolDefinition source : context.sourceTurn().getRequest().getToolsList())
            {
                ConversationLlmToolDefinition definition = new ConversationLlmToolDefinition();
                applyAudit(definition, userId);
                definition.setRoundId(context.turn().getRoundId());
                definition.setTurnId(context.turn().getId());
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
     * the logical {@code (turnId,messageOrder)} key rather than database return order.
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
            for (LlmConversationMessage sourceMessage : context.sourceTurn().getRequest().getMessagesList())
            {
                messages.add(toRequestMessage(
                    sourceMessage, context.turn().getRoundId(), context.turn().getId(), messageOrder, userId));
                sourceMessagesByKey.put(
                    new RequestMessageKey(context.turn().getId(), messageOrder), sourceMessage);
                messageOrder++;
            }
        }

        List<ConversationLlmRequestMessage> savedMessages =
            conversationLlmRequestMessageMapper.insertRequestMessages(messages);
        requireReturnedRows("LLM request message", messages.size(), savedMessages);

        List<ConversationLlmRequestMessageToolCall> requestToolCalls = new ArrayList<>();
        for (ConversationLlmRequestMessage savedMessage : savedMessages)
        {
            RequestMessageKey key = new RequestMessageKey(savedMessage.getTurnId(), savedMessage.getMessageOrder());
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
                toolCall.setRoundId(savedMessage.getRoundId());
                toolCall.setTurnId(savedMessage.getTurnId());
                toolCall.setCallOrder(callOrder++);
                toolCall.setToolCallId(sourceToolCall.getId());
                toolCall.setType(ToolCallType.fromWireValue(sourceToolCall.getType()));
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
        Map<ResponseToolCallKey, ToolCallExecution> sourceExecutionsByKey = new HashMap<>();
        List<ConversationToolCallExecution> executions = new ArrayList<>();
        for (TurnPersistenceContext context : contexts)
        {
            for (ToolCallExecution execution : context.sourceTurn().getToolCallExecutionsList())
                sourceExecutionsByKey.put(
                    new ResponseToolCallKey(context.turn().getId(), execution.getToolCallId()), execution);

            int callOrder = 0;
            for (ToolCall sourceToolCall : context.sourceTurn().getResponse().getMessage().getToolCallsList())
            {
                int currentCallOrder = callOrder++;
                ToolCallExecution sourceExecution = sourceExecutionsByKey.get(
                    new ResponseToolCallKey(context.turn().getId(), sourceToolCall.getId()));
                if (sourceExecution == null)
                    throw new IllegalStateException("Response Tool call has no execution evidence.");
                executions.add(toToolCallExecution(
                    sourceExecution, context.turn().getRoundId(), context.turn().getId(), currentCallOrder,
                    sourceToolCall, userId));
            }
        }

        if (executions.isEmpty())
            return;
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
        return jsonSerializer.serialize(values, "Content parts");
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
            JsonNode root = jsonSerializer.readTree(json, "Persisted content parts");
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
        catch (IllegalArgumentException e)
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
     * Maps one RPC Tool execution and its model-emitted call into the normalized persistence row.
     *
     * @param source RPC execution evidence
     * @param roundId parent Round ID
     * @param turnId parent Turn ID
     * @param callOrder model response order
     * @param toolCall model-emitted Tool call
     * @param userId authenticated owner written to audit columns
     * @return durable Tool execution entity
     */
    private ConversationToolCallExecution toToolCallExecution(
        ToolCallExecution source,
        long roundId,
        long turnId,
        int callOrder,
        ToolCall toolCall,
        long userId)
    {
        ConversationToolCallExecution execution = new ConversationToolCallExecution();
        applyAudit(execution, userId);
        execution.setRoundId(roundId);
        execution.setTurnId(turnId);
        execution.setCallOrder(callOrder);
        execution.setToolCallId(toolCall.getId());
        execution.setType(ToolCallType.fromWireValue(toolCall.getType()));
        execution.setToolName(toolCall.getFunction().getName());
        execution.setArguments(toolCall.getFunction().getArguments());
        execution.setToolKey(source.getToolKey());
        execution.setStatus(switch (source.getStatus())
        {
            case TOOL_CALL_EXECUTION_STATUS_COMPLETED -> ToolCallExecutionStatus.COMPLETED;
            case TOOL_CALL_EXECUTION_STATUS_FAILED -> ToolCallExecutionStatus.FAILED;
            case TOOL_CALL_EXECUTION_STATUS_CANCELLED -> ToolCallExecutionStatus.CANCELLED;
            case TOOL_CALL_EXECUTION_STATUS_UNKNOWN -> ToolCallExecutionStatus.UNKNOWN;
            case TOOL_CALL_EXECUTION_STATUS_REJECTED -> ToolCallExecutionStatus.REJECTED;
            default -> throw new IllegalArgumentException("Unsupported Tool execution status.");
        });
        execution.setResultContent(source.getResultContent().isEmpty() ? null : source.getResultContent());
        execution.setResultContentParts(serializeContentParts(source.getResultContentPartsList()));
        execution.setRawResult(source.hasRawResult() ? source.getRawResult() : null);
        execution.setErrorMessage(source.getErrorMessage());
        execution.setStartTime(Instant.ofEpochMilli(source.getStartTime()));
        execution.setEndTime(Instant.ofEpochMilli(source.getEndTime()));
        return execution;
    }

    /**
     * Builds the immutable projection used by the set-based boundary queries.
     *
     * @param reference public source and boundary selection
     * @return query projection with the same frozen values
     */
    private ConversationReferenceBoundary toBoundary(ConversationReference reference)
    {
        return new ConversationReferenceBoundary(
            reference.getSourceConversationId(), reference.getSourceEndRoundNumber());
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

    /** Carries a source protobuf Turn alongside its normalized database entity.
     * @param sourceTurn Runner Turn payload
     * @param turn normalized entity with generated identity
     */
    private record TurnPersistenceContext(
        ifl.agentbreaker.conversationmanager.rpc.ConversationTurn sourceTurn,
        ConversationTurn turn)
    {
    }

    /** Identifies one request message within a normalized Turn.
     * @param turnId Database identifier of the containing Turn.
     * @param messageOrder Numeric message order used for ordering or bounds.
     */
    private record RequestMessageKey(long turnId, int messageOrder)
    {
    }

    /** Identifies one response Tool call within a normalized Turn.
     * @param turnId Database identifier of the containing Turn.
     * @param toolCallId Provider-generated Tool call identifier.
     */
    private record ResponseToolCallKey(long turnId, String toolCallId)
    {
    }

    /** Durable Round and model Turn selected for provider-neutral replay.
     * @param round active Round owning the stored request snapshot
     * @param turn response Turn appended after that request snapshot
     */
    private record ReplayTurnBoundary(ConversationRound round, ConversationTurn turn)
    {
    }

    /** Identifies one Conversation and frozen Round boundary pair.
     * @param conversationId Stable public identifier of the Conversation.
     * @param roundNumber Numeric round number used for ordering or bounds.
     */
    private record RoundBoundaryKey(String conversationId, long roundNumber)
    {
    }
}
