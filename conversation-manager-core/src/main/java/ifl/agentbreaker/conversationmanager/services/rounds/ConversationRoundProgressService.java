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
    @Autowired private ConversationMapper conversationMapper;
    @Autowired private ConversationRoundMapper roundMapper;
    @Autowired private ConversationRoundMutationMapper mutationMapper;
    @Autowired private ConversationToolDispatchMapper dispatchMapper;
    @Autowired private ConversationTurnMapper turnMapper;
    @Autowired private ConversationRoundService roundService;
    @Autowired private ConversationMutationLock mutationLock;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private ConversationRoundProgressValidator validator;
    @Autowired private ConversationRoundProgressMapper progressMapper;

    @PostConstruct
    public void recoverInterruptedDispatches()
    {
        dispatchMapper.recoverStaleDispatches(Instant.now(), "Runner or provider restarted during remote dispatch.");
    }

    public MutationOutcome create(CreateConversationRoundCheckpointRequest request)
    {
        validator.validateCreate(request);
        String hash = hash(request);
        try (ConversationMutationLock.LockHandle ignored = mutationLock.acquire(request.getConversationId()))
        {
            MutationOutcome outcome = transactionTemplate.execute(status -> createInTransaction(request, hash));
            if (outcome == null)
                throw new IllegalStateException("Checkpoint transaction returned no result.");
            return outcome;
        }
    }

    public MutationOutcome append(AppendConversationRoundProgressRequest request)
    {
        validator.validateAppend(request);
        String hash = hash(request);
        try (ConversationMutationLock.LockHandle ignored = mutationLock.acquire(request.getConversationId()))
        {
            MutationOutcome outcome = transactionTemplate.execute(status -> appendInTransaction(request, hash));
            if (outcome == null)
                throw new IllegalStateException("Progress transaction returned no result.");
            return outcome;
        }
    }

    public MutationOutcome finalizeRound(FinalizeConversationRoundRequest request)
    {
        validator.validateFinalize(request);
        String hash = hash(request);
        try (ConversationMutationLock.LockHandle ignored = mutationLock.acquire(request.getConversationId()))
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
        ConversationRound existing = roundMapper.getRound(request.getConversationId(), request.getRoundNumber());
        if (existing != null)
            return replay(existing, request.getMutationId(), hash);
        if (request.getRoundNumber() != conversation.getLatestRoundNumber() + 1)
            throw validator.invalid("round_number must equal the persisted high-water mark plus one.");

        ConversationRound round = roundMapper.insertCheckpoint(progressMapper.toCheckpoint(request, hash));
        if (round == null)
            throw new IllegalStateException("Checkpoint insert returned no row.");
        SaveConversationRoundRequest compatibility = progressMapper.toCompatibilityRequest(request);
        roundService.persistRoundFiles(compatibility, round.getId());
        roundService.persistRoundReferences(compatibility, conversation, round.getId());
        if (conversationMapper.advanceLatestRoundNumber(request.getConversationId(), request.getUserId(),
            request.getRoundNumber(), request.getUserRequest().getContent(), "New Conversation") != 1)
            throw new IllegalStateException("Failed to advance Conversation high-water mark.");
        recordMutation(round.getId(), request.getMutationId(), hash, 0);
        return new MutationOutcome(0, RoundStatus.ROUND_STATUS_IN_PROGRESS, false);
    }

    private MutationOutcome appendInTransaction(AppendConversationRoundProgressRequest request, String hash)
    {
        requireConversation(request.getUserId(), request.getConversationId());
        ConversationRound round = requireRound(request.getConversationId(), request.getRoundNumber());
        ConversationRoundMutation replay = mutationMapper.getMutation(round.getId(), request.getMutationId());
        if (replay != null)
            return validateReplay(replay, hash, RoundStatus.ROUND_STATUS_IN_PROGRESS);
        validator.requireMutableRevision(round, request.getExpectedRevision());
        validateTurnBoundary(round.getId(), request);

        if (request.getTurnsCount() > 0)
            roundService.persistTurnsAndChildren(SaveConversationRoundRequest.newBuilder()
                .setUserId(request.getUserId()).addAllTurns(request.getTurnsList()).build(), round.getId());
        List<ConversationToolDispatch> dispatches = progressMapper.toDispatches(
            round.getId(), request.getDispatchEvidenceList());
        if (!dispatches.isEmpty() && dispatchMapper.upsertDispatchEvidence(dispatches) != dispatches.size())
            throw validator.invalid("Dispatch evidence attempted to overwrite terminal evidence.");
        if (roundMapper.advanceRevision(round.getId(), request.getExpectedRevision(), request.getUserId()) != 1)
            throw validator.stale();
        long committedRevision = request.getExpectedRevision() + 1;
        recordMutation(round.getId(), request.getMutationId(), hash, committedRevision);
        return new MutationOutcome(committedRevision, RoundStatus.ROUND_STATUS_IN_PROGRESS, false);
    }

    private MutationOutcome finalizeInTransaction(FinalizeConversationRoundRequest request, String hash)
    {
        requireConversation(request.getUserId(), request.getConversationId());
        ConversationRound round = requireRound(request.getConversationId(), request.getRoundNumber());
        ConversationRoundMutation replay = mutationMapper.getMutation(round.getId(), request.getMutationId());
        if (replay != null)
            return validateReplay(replay, hash, request.getStatus());
        validator.requireMutableRevision(round, request.getExpectedRevision());
        String answer = request.hasFinalAnswer() && StringUtils.hasText(request.getFinalAnswer().getContent())
            ? request.getFinalAnswer().getContent() : null;
        String parts = request.hasFinalAnswer() && request.getFinalAnswer().getContentPartsCount() > 0
            ? progressMapper.serializeContentParts(request.getFinalAnswer().getContentPartsList()) : null;
        Long sourceTurn = request.hasFinalAnswer() ? request.getFinalAnswer().getSourceTurnNumber() : null;
        if (roundMapper.finalizeRound(round.getId(), request.getExpectedRevision(), request.getUserId(),
            request.getStatus().name().replace("ROUND_STATUS_", ""), answer, parts, sourceTurn,
            request.getErrorMessage(), Instant.ofEpochMilli(request.getEndTime())) != 1)
            throw validator.stale();
        long committedRevision = request.getExpectedRevision() + 1;
        recordMutation(round.getId(), request.getMutationId(), hash, committedRevision);
        return new MutationOutcome(committedRevision, request.getStatus(), false);
    }

    private void validateTurnBoundary(long roundId, AppendConversationRoundProgressRequest request)
    {
        long nextTurn = turnMapper.countTurns(roundId) + 1;
        for (int index = 0; index < request.getTurnsCount(); index++)
            if (request.getTurns(index).getTurnNumber() != nextTurn + index)
                throw validator.invalid("Appended turn_number must continue the persisted sequence.");
    }

    private Conversation requireConversation(long userId, String conversationId)
    {
        Conversation conversation = conversationMapper.lockConversationByIdAndUser(conversationId, userId);
        if (conversation == null)
            throw validator.error(ConversationErrorCode.CONVERSATION_ERROR_CODE_CONVERSATION_NOT_FOUND,
                "Conversation does not exist.");
        return conversation;
    }

    private ConversationRound requireRound(String conversationId, long roundNumber)
    {
        ConversationRound round = roundMapper.getRound(conversationId, roundNumber);
        if (round == null || round.isDeleted())
            throw validator.error(ConversationErrorCode.CONVERSATION_ERROR_CODE_ROUND_NOT_FOUND,
                "Round does not exist.");
        return round;
    }

    private MutationOutcome replay(ConversationRound round, String mutationId, String hash)
    {
        ConversationRoundMutation mutation = mutationMapper.getMutation(round.getId(), mutationId);
        if (mutation == null)
            throw validator.error(ConversationErrorCode.CONVERSATION_ERROR_CODE_ROUND_NUMBER_CONFLICT,
                "Round number already contains different persisted content.");
        return validateReplay(mutation, hash, toProtoStatus(round.getStatus()));
    }

    private MutationOutcome validateReplay(ConversationRoundMutation mutation, String hash, RoundStatus status)
    {
        if (!mutation.getPayloadHash().equals(hash))
            throw validator.error(ConversationErrorCode.CONVERSATION_ERROR_CODE_MUTATION_CONFLICT,
                "mutation_id was already committed with different content.");
        return new MutationOutcome(mutation.getCommittedRevision(), status, true);
    }

    private void recordMutation(long roundId, String mutationId, String hash, long revision)
    {
        ConversationRoundMutation mutation = new ConversationRoundMutation();
        mutation.setRoundId(roundId);
        mutation.setMutationId(mutationId);
        mutation.setPayloadHash(hash);
        mutation.setCommittedRevision(revision);
        if (mutationMapper.insertMutation(mutation) != 1)
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

    public record MutationOutcome(long revision, RoundStatus status, boolean idempotentReplay)
    {
    }
}
