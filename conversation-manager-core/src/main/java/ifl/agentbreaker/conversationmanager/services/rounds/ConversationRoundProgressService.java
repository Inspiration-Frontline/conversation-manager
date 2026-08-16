package ifl.agentbreaker.conversationmanager.services.rounds;

import com.google.protobuf.MessageLite;
import ifl.agentbreaker.conversationmanager.dao.ConversationMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationRoundMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationRoundMutationMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationToolDispatchMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationTurnMapper;
import ifl.agentbreaker.conversationmanager.domain.constants.ConversationRoundStatus;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.Conversation;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationRound;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationRoundMutation;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationToolDispatch;
import ifl.agentbreaker.conversationmanager.rpc.AppendConversationRoundProgressRequest;
import ifl.agentbreaker.conversationmanager.rpc.ConversationErrorCode;
import ifl.agentbreaker.conversationmanager.rpc.CreateConversationRoundCheckpointRequest;
import ifl.agentbreaker.conversationmanager.rpc.FinalizeConversationRoundRequest;
import ifl.agentbreaker.conversationmanager.rpc.RoundStatus;
import ifl.agentbreaker.conversationmanager.rpc.SaveConversationRoundRequest;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@Service
public class ConversationRoundProgressService
{
    @Autowired
    private ConversationMapper conversationMapper;

    @Autowired
    private ConversationRoundMapper conversationRoundMapper;

    @Autowired
    private ConversationRoundMutationMapper conversationRoundMutationMapper;

    @Autowired
    private ConversationToolDispatchMapper conversationToolDispatchMapper;

    @Autowired
    private ConversationTurnMapper conversationTurnMapper;

    @Autowired
    private ConversationRoundService conversationRoundService;

    @Autowired
    private ConversationMutationLock conversationMutationLock;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ConversationRoundProgressValidator conversationRoundProgressValidator;

    @Autowired
    private ConversationRoundProgressMapper conversationRoundProgressMapper;

    /**
     * Marks delivery attempts left in progress by an interrupted Runner as UNKNOWN at application startup.
     */
    @PostConstruct
    public void recoverInterruptedDispatches()
    {
        conversationToolDispatchMapper.recoverStaleDispatches(Instant.now(), "Runner or provider restarted during remote dispatch.");
    }

    /**
     * Creates the immutable Round checkpoint before the first model call.
     *
     * @param request authenticated checkpoint mutation with the frozen user request and MCP bindings
     * @return committed revision and whether the request replayed a previously committed mutation
     */
    public MutationOutcome create(CreateConversationRoundCheckpointRequest request)
    {
        conversationRoundProgressValidator.validateCreate(request);
        String hash = hash(request);
        try (ConversationMutationLock.LockHandle ignored = conversationMutationLock.acquire(request.getConversationId()))
        {
            MutationOutcome outcome = transactionTemplate.execute(status -> createInTransaction(request, hash));
            if (outcome == null)
                throw new IllegalStateException("Checkpoint transaction returned no result.");
            return outcome;
        }
    }

    /**
     * Appends ordered Turn or MCP dispatch evidence while enforcing the caller's expected revision.
     *
     * @param request authenticated append mutation
     * @return committed revision and whether the request replayed a previously committed mutation
     */
    public MutationOutcome append(AppendConversationRoundProgressRequest request)
    {
        conversationRoundProgressValidator.validateAppend(request);
        String hash = hash(request);
        try (ConversationMutationLock.LockHandle ignored = conversationMutationLock.acquire(request.getConversationId()))
        {
            MutationOutcome outcome = transactionTemplate.execute(status -> appendInTransaction(request, hash));
            if (outcome == null)
                throw new IllegalStateException("Progress transaction returned no result.");
            return outcome;
        }
    }

    /**
     * Commits the terminal state and optional final answer of an in-progress Round.
     *
     * @param request authenticated finalization mutation
     * @return committed revision and whether the request replayed a previously committed mutation
     */
    public MutationOutcome finalizeRound(FinalizeConversationRoundRequest request)
    {
        conversationRoundProgressValidator.validateFinalize(request);
        String hash = hash(request);
        try (ConversationMutationLock.LockHandle ignored = conversationMutationLock.acquire(request.getConversationId()))
        {
            MutationOutcome outcome = transactionTemplate.execute(status -> finalizeInTransaction(request, hash));
            if (outcome == null)
                throw new IllegalStateException("Finalize transaction returned no result.");
            return outcome;
        }
    }

    private MutationOutcome createInTransaction(CreateConversationRoundCheckpointRequest request, String hash)
    {
        Conversation conversation = requireConversation(request.getUserId(), request.getConversationId());
        ConversationRound existing = conversationRoundMapper.getRound(request.getConversationId(), request.getRoundNumber());
        if (existing != null)
            return replay(existing, request.getMutationId(), hash);
        if (request.getRoundNumber() != conversation.getLatestRoundNumber() + 1)
            throw conversationRoundProgressValidator.invalid("round_number must equal the persisted high-water mark plus one.");

        ConversationRound round = conversationRoundMapper.insertCheckpoint(conversationRoundProgressMapper.toCheckpoint(request, hash));
        if (round == null)
            throw new IllegalStateException("Checkpoint insert returned no row.");
        SaveConversationRoundRequest legacyRoundSaveRequest = conversationRoundProgressMapper.toLegacyRoundSaveRequest(request);
        conversationRoundService.persistRoundFiles(legacyRoundSaveRequest, round.getId());
        conversationRoundService.persistRoundReferences(legacyRoundSaveRequest, conversation, round.getId());
        if (conversationMapper.advanceLatestRoundNumber(request.getConversationId(), request.getUserId(),
            request.getRoundNumber(), request.getUserRequest().getContent(), "New Conversation") != 1)
            throw new IllegalStateException("Failed to advance Conversation high-water mark.");
        recordMutation(round.getId(), request.getUserId(), request.getMutationId(), hash, 0);
        return new MutationOutcome(0, RoundStatus.ROUND_STATUS_IN_PROGRESS, false);
    }

    private MutationOutcome appendInTransaction(AppendConversationRoundProgressRequest request, String hash)
    {
        requireConversation(request.getUserId(), request.getConversationId());
        ConversationRound round = requireRound(request.getConversationId(), request.getRoundNumber());
        ConversationRoundMutation replay = conversationRoundMutationMapper.getMutation(round.getId(), request.getMutationId());
        if (replay != null)
            return validateReplay(replay, hash, RoundStatus.ROUND_STATUS_IN_PROGRESS);
        conversationRoundProgressValidator.requireMutableRevision(round, request.getExpectedRevision());
        validateTurnBoundary(round.getId(), request);

        if (request.getTurnsCount() > 0)
            conversationRoundService.persistTurnsAndChildren(SaveConversationRoundRequest.newBuilder()
                .setUserId(request.getUserId()).addAllTurns(request.getTurnsList()).build(), round.getId());
        List<ConversationToolDispatch> dispatches = conversationRoundProgressMapper.toDispatches(
            request.getUserId(), round.getId(), request.getDispatchEvidenceList());
        if (!dispatches.isEmpty() && conversationToolDispatchMapper.upsertDispatchEvidence(dispatches) != dispatches.size())
            throw conversationRoundProgressValidator.invalid("Dispatch evidence attempted to overwrite terminal evidence.");
        if (conversationRoundMapper.advanceRevision(round.getId(), request.getExpectedRevision(), request.getUserId()) != 1)
            throw conversationRoundProgressValidator.stale();
        long committedRevision = request.getExpectedRevision() + 1;
        recordMutation(round.getId(), request.getUserId(), request.getMutationId(), hash, committedRevision);
        return new MutationOutcome(committedRevision, RoundStatus.ROUND_STATUS_IN_PROGRESS, false);
    }

    private MutationOutcome finalizeInTransaction(FinalizeConversationRoundRequest request, String hash)
    {
        requireConversation(request.getUserId(), request.getConversationId());
        ConversationRound round = requireRound(request.getConversationId(), request.getRoundNumber());
        ConversationRoundMutation replay = conversationRoundMutationMapper.getMutation(round.getId(), request.getMutationId());
        if (replay != null)
            return validateReplay(replay, hash, request.getStatus());
        conversationRoundProgressValidator.requireMutableRevision(round, request.getExpectedRevision());

        String answer = request.hasFinalAnswer() && StringUtils.hasText(request.getFinalAnswer().getContent())
            ? request.getFinalAnswer().getContent() : null;
        String parts = request.hasFinalAnswer() && request.getFinalAnswer().getContentPartsCount() > 0
            ? conversationRoundProgressMapper.serializeContentParts(request.getFinalAnswer().getContentPartsList()) : null;
        Long sourceTurn = request.hasFinalAnswer() ? request.getFinalAnswer().getSourceTurnNumber() : null;

        if (conversationRoundMapper.finalizeRound(round.getId(), request.getExpectedRevision(), request.getUserId(),
            request.getStatus().name().replace("ROUND_STATUS_", ""), answer, parts, sourceTurn,
            request.getErrorMessage(), Instant.ofEpochMilli(request.getEndTime())) != 1)
            throw conversationRoundProgressValidator.stale();

        long committedRevision = request.getExpectedRevision() + 1;
        recordMutation(round.getId(), request.getUserId(), request.getMutationId(), hash, committedRevision);
        return new MutationOutcome(committedRevision, request.getStatus(), false);
    }

    private void validateTurnBoundary(long roundId, AppendConversationRoundProgressRequest request)
    {
        long nextTurn = conversationTurnMapper.countTurns(roundId) + 1;
        for (int index = 0; index < request.getTurnsCount(); index++)
            if (request.getTurns(index).getTurnNumber() != nextTurn + index)
                throw conversationRoundProgressValidator.invalid("Appended turn_number must continue the persisted sequence.");
    }

    private Conversation requireConversation(long userId, String conversationId)
    {
        Conversation conversation = conversationMapper.lockConversationByIdAndUser(conversationId, userId);
        if (conversation == null)
            throw conversationRoundProgressValidator.error(ConversationErrorCode.CONVERSATION_ERROR_CODE_CONVERSATION_NOT_FOUND,
                "Conversation does not exist.");
        return conversation;
    }

    private ConversationRound requireRound(String conversationId, long roundNumber)
    {
        ConversationRound round = conversationRoundMapper.getRound(conversationId, roundNumber);
        if (round == null || round.isDeleted())
            throw conversationRoundProgressValidator.error(ConversationErrorCode.CONVERSATION_ERROR_CODE_ROUND_NOT_FOUND,
                "Round does not exist.");
        return round;
    }

    private MutationOutcome replay(ConversationRound round, String mutationId, String hash)
    {
        ConversationRoundMutation mutation = conversationRoundMutationMapper.getMutation(round.getId(), mutationId);
        if (mutation == null)
            throw conversationRoundProgressValidator.error(ConversationErrorCode.CONVERSATION_ERROR_CODE_ROUND_NUMBER_CONFLICT,
                "Round number already contains different persisted content.");
        return validateReplay(mutation, hash, toProtoStatus(round.getStatus()));
    }

    private MutationOutcome validateReplay(ConversationRoundMutation mutation, String hash, RoundStatus status)
    {
        if (!mutation.getPayloadHash().equals(hash))
            throw conversationRoundProgressValidator.error(ConversationErrorCode.CONVERSATION_ERROR_CODE_MUTATION_CONFLICT,
                "mutation_id was already committed with different content.");
        return new MutationOutcome(mutation.getCommittedRevision(), status, true);
    }

    private void recordMutation(long roundId, long userId, String mutationId, String hash, long revision)
    {
        ConversationRoundMutation mutation = new ConversationRoundMutation();
        mutation.setCreatorId(userId);
        mutation.setModifierId(userId);
        mutation.setRoundId(roundId);
        mutation.setMutationId(mutationId);
        mutation.setPayloadHash(hash);
        mutation.setCommittedRevision(revision);
        if (conversationRoundMutationMapper.insertMutation(mutation) != 1)
            throw new IllegalStateException("Mutation ledger insert affected an unexpected row count.");
    }

    private String hash(MessageLite message)
    {
        try
        {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(message.toByteArray()));
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("SHA-256 is unavailable.", e);
        }
    }

    private RoundStatus toProtoStatus(ConversationRoundStatus status)
    {
        return RoundStatus.valueOf("ROUND_STATUS_" + status.name());
    }

    /**
     * Result of one mutation request.
     *
     * @param revision revision committed by the original mutation
     * @param status persisted Round status after that mutation
     * @param idempotentReplay {@code true} when the same {@code mutation_id} and identical SHA-256
     * payload were already committed; {@code false} when this request applied a new database change
     */
    public record MutationOutcome(long revision, RoundStatus status, boolean idempotentReplay)
    {
    }
}
