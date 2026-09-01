package ifl.agentbreaker.conversationmanager.services.rounds;

import ifl.agentbreaker.conversationmanager.dao.ConversationMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationRoundMapper;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.RoundDeletionFailure;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.RoundDeletionResult;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.Conversation;
import ifl.agentbreaker.conversationmanager.rpc.ConversationErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/** Validates and logically deletes active Round suffixes without reducing the high-water mark. */
@Service
public class ConversationRoundDeletionService
{
    /** Mapper used to lock and authorize the parent Conversation. */
    @Autowired
    private ConversationMapper conversationMapper;

    /** Mapper used to load the complete active Round-number set once. */
    @Autowired
    private ConversationRoundMapper conversationRoundMapper;

    /** Independent transaction boundary for each requested Round tombstone. */
    @Autowired
    private ConversationRoundDeletionTransactionService conversationRoundDeletionTransactionService;

    /** Distributed aggregate lock shared with Round persistence. */
    @Autowired
    private ConversationMutationLock conversationMutationLock;

    /** Read transaction used to lock and verify the parent Conversation. */
    @Autowired
    private TransactionTemplate transactionTemplate;

    /**
     * Deletes a validated active suffix in descending order under one aggregate lock.
     *
     * @param userId authenticated Conversation owner
     * @param conversationId stable Conversation identifier
     * @param roundNumbers positive unique Round numbers forming the active tail
     * @return deleted values and any failed or unattempted values
     */
    public RoundDeletionResult deleteRounds(long userId, String conversationId, List<Long> roundNumbers)
    {
        List<Long> requested = validateRequest(userId, conversationId, roundNumbers);
        try (ConversationMutationLock.LockHandle ignored = conversationMutationLock.acquire(conversationId))
        {
            requireOwnedConversation(userId, conversationId);
            List<Long> activeRoundNumbers = conversationRoundMapper.listActiveRoundNumbers(conversationId);
            validateTailSuffix(requested, activeRoundNumbers);

            List<Long> descending = new ArrayList<>(requested);
            descending.sort(Collections.reverseOrder());
            return deleteDescending(userId, conversationId, descending);
        }
    }

    /** Validates request shape before acquiring any lock.
     * @param userId authenticated owner
     * @param conversationId stable Conversation identifier
     * @param roundNumbers requested Round numbers
     * @return sorted immutable request values
     */
    private List<Long> validateRequest(long userId, String conversationId, List<Long> roundNumbers)
    {
        if (userId <= 0 || !StringUtils.hasText(conversationId) || roundNumbers == null || roundNumbers.isEmpty()
            || roundNumbers.stream().anyMatch(roundNumber -> roundNumber == null || roundNumber <= 0)
            || new HashSet<>(roundNumbers).size() != roundNumbers.size())
            throw error(ConversationErrorCode.CONVERSATION_ERROR_CODE_INVALID_REQUEST,
                "Round deletion requires positive unique Round numbers.");
        return roundNumbers.stream().sorted().toList();
    }

    /** Locks and verifies the parent Conversation in a short read transaction.
     * @param userId authenticated owner
     * @param conversationId stable Conversation identifier
     */
    private void requireOwnedConversation(long userId, String conversationId)
    {
        Conversation conversation = transactionTemplate.execute(
            status -> conversationMapper.lockConversationByIdAndUser(conversationId, userId));
        if (conversation == null)
            throw error(ConversationErrorCode.CONVERSATION_ERROR_CODE_CONVERSATION_NOT_FOUND,
                "Conversation does not exist.");
    }

    /** Validates that the requested values exactly match an active contiguous tail.
     * @param requested ascending requested Round numbers
     * @param activeRoundNumbers ascending active Round numbers
     */
    private void validateTailSuffix(List<Long> requested, List<Long> activeRoundNumbers)
    {
        int startIndex = activeRoundNumbers.size() - requested.size();
        if (startIndex < 0 || !activeRoundNumbers.subList(startIndex, activeRoundNumbers.size()).equals(requested))
            throw error(ConversationErrorCode.CONVERSATION_ERROR_CODE_DELETE_REQUIRES_TAIL_SUFFIX,
                "Round deletion requires a contiguous suffix ending at the latest active Round.");
    }

    /** Deletes the validated suffix in one set-based transaction.
     * @param userId authenticated owner
     * @param conversationId stable Conversation identifier
     * @param descending requested Round numbers in deletion order
     * @return partial-success-aware deletion result
     */
    private RoundDeletionResult deleteDescending(long userId, String conversationId, List<Long> descending)
    {
        boolean deleted = conversationRoundDeletionTransactionService.tombstoneRounds(
            conversationId, descending, userId);
        if (deleted)
            return new RoundDeletionResult(List.copyOf(descending), List.of());

        List<RoundDeletionFailure> failures = descending.stream()
            .map(value -> new RoundDeletionFailure(
                value,
                ConversationErrorCode.CONVERSATION_ERROR_CODE_ROUND_NOT_FOUND_VALUE,
                "Round was not deleted."))
            .toList();
        return new RoundDeletionResult(List.of(), failures);
    }

    /** Creates a typed domain failure.
     * @param code stable protocol error code
     * @param message client-safe explanation
     * @return deletion validation exception
     */
    private RoundPersistenceException error(ConversationErrorCode code, String message)
    {
        return new RoundPersistenceException(code.getNumber(), message);
    }
}
