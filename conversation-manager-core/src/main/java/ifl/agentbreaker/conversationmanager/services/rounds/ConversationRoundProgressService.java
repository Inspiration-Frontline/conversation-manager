package ifl.agentbreaker.conversationmanager.services.rounds;

import com.google.protobuf.MessageLite;
import ifl.agentbreaker.conversationmanager.dao.ConversationMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationRoundMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationRoundMutationMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationToolDispatchMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationTurnMapper;
import ifl.agentbreaker.conversationmanager.domain.constants.ConversationRoundStatus;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.Conversation;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.FileResource;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationRound;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationRoundMutation;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationToolDispatch;
import ifl.agentbreaker.conversationmanager.rpc.AppendConversationRoundProgressRequest;
import ifl.agentbreaker.conversationmanager.rpc.ConversationErrorCode;
import ifl.agentbreaker.conversationmanager.rpc.CreateConversationRoundCheckpointRequest;
import ifl.agentbreaker.conversationmanager.rpc.FinalizeConversationRoundRequest;
import ifl.agentbreaker.conversationmanager.rpc.RoundStatus;
import ifl.agentbreaker.conversationmanager.rpc.SaveConversationRoundRequest;
import ifl.agentbreaker.conversationmanager.support.ConversationTitleManager;
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

/** Applies idempotent checkpoint, append, and finalize mutations to one in-progress Round. */
@Service
public class ConversationRoundProgressService
{
    /** Mapper for Conversation ownership and monotonic high-water updates. */
    @Autowired
    private ConversationMapper conversationMapper;

    /** Mapper for Round checkpoints, revisions, and terminal state. */
    @Autowired
    private ConversationRoundMapper conversationRoundMapper;

    /** Mapper for the immutable mutation idempotency ledger. */
    @Autowired
    private ConversationRoundMutationMapper conversationRoundMutationMapper;

    /** Mapper for durable remote Tool delivery evidence and startup recovery. */
    @Autowired
    private ConversationToolDispatchMapper conversationToolDispatchMapper;

    /** Mapper used to validate the persisted Turn append boundary. */
    @Autowired
    private ConversationTurnMapper conversationTurnMapper;

    /** Service that persists normalized Turns and their child rows in batches. */
    @Autowired
    private ConversationRoundService conversationRoundService;

    /** Distributed short-lived lock serializing mutations of one Conversation aggregate. */
    @Autowired
    private ConversationMutationLock conversationMutationLock;

    /** Transaction boundary used after the distributed mutation lease is acquired. */
    @Autowired
    private TransactionTemplate transactionTemplate;

    /** Validator for command shape, optimistic revision, and mutable-state invariants. */
    @Autowired
    private ConversationRoundProgressValidator conversationRoundProgressValidator;

    /** Mapper component converting protobuf progress commands into persistence entities. */
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

    /**
     * Creates the initial persisted Round, freezes request attachments/references, and records the
     * first mutation in the same transaction.
     *
     * @param request validated checkpoint command
     * @param hash SHA-256 of the complete protobuf command bytes
     * @return committed initial revision or an idempotent replay result
     */
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
        List<FileResource> roundFiles = conversationRoundService.persistRoundFiles(
            legacyRoundSaveRequest, round.getId());
        conversationRoundService.persistRoundReferences(legacyRoundSaveRequest, conversation, round.getId());
        String automaticTitle = deriveAutomaticTitle(request, roundFiles);
        if (conversationMapper.advanceLatestRoundNumber(request.getConversationId(), request.getUserId(),
            request.getRoundNumber(), automaticTitle, ConversationTitleManager.DEFAULT_TITLE) != 1)
            throw new IllegalStateException("Failed to advance Conversation high-water mark.");
        recordMutation(round.getId(), request.getUserId(), request.getMutationId(), hash, 0);
        return new MutationOutcome(0, RoundStatus.ROUND_STATUS_IN_PROGRESS, false);
    }

    /**
     * Derives the first-Round title from visible text or the first ordered attachment.
     *
     * @param request checkpoint containing the frozen user request
     * @param roundFiles ordered attachment resources persisted for the Round
     * @return non-blank normalized title
     */
    private String deriveAutomaticTitle(
        CreateConversationRoundCheckpointRequest request, List<FileResource> roundFiles)
    {
        if (StringUtils.hasText(request.getUserRequest().getContent()))
            return ConversationTitleManager.deriveFromFirstUserMessage(request.getUserRequest().getContent());
        if (!roundFiles.isEmpty())
            return ConversationTitleManager.deriveFromAttachmentFilename(roundFiles.get(0).getOriginalFilename());
        return ConversationTitleManager.DEFAULT_TITLE;
    }

    /**
     * Appends new immutable evidence and advances the optimistic revision in one transaction.
     *
     * @param request validated append command
     * @param hash SHA-256 of the complete protobuf command bytes
     * @return committed next revision or an idempotent replay result
     */
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

    /**
     * Transitions an in-progress Round to a terminal status and records its mutation atomically.
     *
     * @param request validated finalization command
     * @param hash SHA-256 of the complete protobuf command bytes
     * @return committed terminal revision or an idempotent replay result
     */
    private MutationOutcome finalizeInTransaction(FinalizeConversationRoundRequest request, String hash)
    {
        requireConversation(request.getUserId(), request.getConversationId());
        ConversationRound round = requireRound(request.getConversationId(), request.getRoundNumber());
        ConversationRoundMutation replay = conversationRoundMutationMapper.getMutation(round.getId(), request.getMutationId());
        if (replay != null)
            return validateReplay(replay, hash, request.getStatus());

        conversationRoundProgressValidator.requireMutableRevision(round, request.getExpectedRevision());

        // Terminal payload is assembled only after validation so a rejected command never does
        // unnecessary serialization work or obscures the stale-revision branch above.
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

    /**
     * Verifies that newly appended Turns continue immediately after the persisted Turn sequence.
     *
     * @param roundId database ID of the target Round
     * @param request validated append command carrying ordered Turns
     */
    private void validateTurnBoundary(long roundId, AppendConversationRoundProgressRequest request)
    {
        long nextTurn = conversationTurnMapper.countTurns(roundId) + 1;
        for (int index = 0; index < request.getTurnsCount(); index++)
            if (request.getTurns(index).getTurnNumber() != nextTurn + index)
                throw conversationRoundProgressValidator.invalid("Appended turn_number must continue the persisted sequence.");
    }

    /**
     * Locks and returns the owned Conversation that contains the target Round.
     *
     * @param userId authenticated owner ID
     * @param conversationId stable public Conversation ID
     * @return locked owned Conversation
     */
    private Conversation requireConversation(long userId, String conversationId)
    {
        Conversation conversation = conversationMapper.lockConversationByIdAndUser(conversationId, userId);
        if (conversation == null)
            throw conversationRoundProgressValidator.error(ConversationErrorCode.CONVERSATION_ERROR_CODE_CONVERSATION_NOT_FOUND,
                "Conversation does not exist.");
        return conversation;
    }

    /**
     * Returns one non-deleted Round after its parent Conversation ownership has already been checked.
     *
     * @param conversationId stable public Conversation ID
     * @param roundNumber one-based Round number
     * @return mutable or terminal persisted Round
     */
    private ConversationRound requireRound(String conversationId, long roundNumber)
    {
        ConversationRound round = conversationRoundMapper.getRound(conversationId, roundNumber);
        if (round == null || round.isDeleted())
            throw conversationRoundProgressValidator.error(ConversationErrorCode.CONVERSATION_ERROR_CODE_ROUND_NOT_FOUND,
                "Round does not exist.");
        return round;
    }

    /**
     * Resolves a checkpoint retry against the mutation ledger of the already existing Round.
     *
     * @param round existing Round at the requested number
     * @param mutationId caller's command identity
     * @param hash SHA-256 of the retry command bytes
     * @return original commit result when the retry is byte-for-byte identical
     */
    private MutationOutcome replay(ConversationRound round, String mutationId, String hash)
    {
        ConversationRoundMutation mutation = conversationRoundMutationMapper.getMutation(round.getId(), mutationId);
        if (mutation == null)
            throw conversationRoundProgressValidator.error(ConversationErrorCode.CONVERSATION_ERROR_CODE_ROUND_NUMBER_CONFLICT,
                "Round number already contains different persisted content.");
        return validateReplay(mutation, hash, toProtoStatus(round.getStatus()));
    }

    /**
     * Accepts an identical committed command or rejects one mutation ID reused with new content.
     *
     * @param mutation immutable ledger record found by Round ID and mutation ID
     * @param hash SHA-256 of the caller's current request bytes
     * @param status current or requested Round status returned to the caller
     * @return idempotent replay outcome without a second database change
     */
    private MutationOutcome validateReplay(ConversationRoundMutation mutation, String hash, RoundStatus status)
    {
        if (!mutation.getPayloadHash().equals(hash))
            throw conversationRoundProgressValidator.error(ConversationErrorCode.CONVERSATION_ERROR_CODE_MUTATION_CONFLICT,
                "mutation_id was already committed with different content.");
        return new MutationOutcome(mutation.getCommittedRevision(), status, true);
    }

    /**
     * Inserts the immutable ledger record proving that one mutation command committed exactly once.
     *
     * @param roundId database ID of the affected Round
     * @param userId authenticated caller used for audit fields
     * @param mutationId unique identity supplied by the caller for retries
     * @param hash SHA-256 of the complete protobuf command bytes
     * @param revision revision committed by this command
     */
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

    /**
     * Computes the canonical replay hash from protobuf wire bytes rather than from a lossy JSON view.
     *
     * @param message complete mutation command
     * @return lowercase hexadecimal SHA-256 digest
     */
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

    /**
     * Maps the persisted enum naming convention back to its protobuf counterpart.
     *
     * @param status persisted Round status
     * @return equivalent protobuf status
     */
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
