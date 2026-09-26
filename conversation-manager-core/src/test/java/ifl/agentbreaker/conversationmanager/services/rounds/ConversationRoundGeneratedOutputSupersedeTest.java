package ifl.agentbreaker.conversationmanager.services.rounds;

import ifl.agentbreaker.conversationmanager.dao.ConversationMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationRoundGeneratedFileMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationRoundMapper;
import ifl.agentbreaker.conversationmanager.dao.FileResourceMapper;
import ifl.agentbreaker.conversationmanager.domain.constants.ConversationRoundStatus;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.Conversation;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationRound;
import ifl.agentbreaker.conversationmanager.rpc.ConversationErrorCode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/** Verifies completed-Round generated-output supersede without deleting durable resources. */
@ExtendWith(MockitoExtension.class)
class ConversationRoundGeneratedOutputSupersedeTest
{
    /** Stable owner used by every mutation assertion. */
    private static final long USER_ID = 7L;

    /** Stable Conversation containing the generated source Round. */
    private static final String CONVERSATION_ID = "conv_regenerate";

    /** Parent Conversation mapper used for ownership checks. */
    @Mock
    private ConversationMapper conversationMapper;

    /** Round mapper used to resolve the completed source Round. */
    @Mock
    private ConversationRoundMapper conversationRoundMapper;

    /** Generated-output relation mapper under test. */
    @Mock
    private ConversationRoundGeneratedFileMapper conversationRoundGeneratedFileMapper;

    /** File-resource mapper that must not be called by presentation-state mutation. */
    @Mock
    private FileResourceMapper fileResourceMapper;

    /** Aggregate mutation lock shared with Round persistence. */
    @Mock
    private ConversationMutationLock conversationMutationLock;

    /** Lock handle released after each service operation. */
    @Mock
    private ConversationMutationLock.LockHandle lockHandle;

    /** Transaction enclosing ownership, validation, and set-based mutation. */
    @Mock
    private TransactionTemplate transactionTemplate;

    /** Consolidated Round service under test. */
    @InjectMocks
    private ConversationRoundService conversationRoundService;

    /** Rejects malformed identities before acquiring a mutation lock. */
    @Test
    void rejectsInvalidRequestBeforeMutation()
    {
        RoundPersistenceException error = Assertions.assertThrows(
            RoundPersistenceException.class,
            () -> conversationRoundService.supersedeGeneratedOutputs(0, "", 0));

        Assertions.assertEquals(
            ConversationErrorCode.CONVERSATION_ERROR_CODE_INVALID_REQUEST_VALUE,
            error.getCode());
        Mockito.verifyNoInteractions(conversationMutationLock, conversationRoundGeneratedFileMapper);
    }

    /** Rejects a Conversation that does not belong to the authenticated owner. */
    @Test
    void rejectsConversationOutsideOwnerBoundary()
    {
        configureTransactionBoundary();
        Mockito.when(conversationMapper.lockConversationByIdAndUser(CONVERSATION_ID, USER_ID))
            .thenReturn(null);

        RoundPersistenceException error = Assertions.assertThrows(
            RoundPersistenceException.class,
            () -> conversationRoundService.supersedeGeneratedOutputs(USER_ID, CONVERSATION_ID, 3));

        Assertions.assertEquals(
            ConversationErrorCode.CONVERSATION_ERROR_CODE_CONVERSATION_NOT_FOUND_VALUE,
            error.getCode());
        Mockito.verifyNoInteractions(conversationRoundGeneratedFileMapper, fileResourceMapper);
    }

    /** Rejects missing and logically deleted source Rounds. */
    @Test
    void rejectsMissingOrDeletedRound()
    {
        configureOwnedConversation();
        ConversationRound deletedRound = completedRound();
        deletedRound.setDeleted(true);
        Mockito.when(conversationRoundMapper.getRound(CONVERSATION_ID, 3L)).thenReturn(deletedRound);

        RoundPersistenceException error = Assertions.assertThrows(
            RoundPersistenceException.class,
            () -> conversationRoundService.supersedeGeneratedOutputs(USER_ID, CONVERSATION_ID, 3));

        Assertions.assertEquals(
            ConversationErrorCode.CONVERSATION_ERROR_CODE_ROUND_NOT_FOUND_VALUE,
            error.getCode());
        Mockito.verifyNoInteractions(conversationRoundGeneratedFileMapper, fileResourceMapper);
    }

    /** Rejects failed or cancelled Rounds because Regenerate is completed-output behavior. */
    @Test
    void rejectsRoundThatIsNotCompleted()
    {
        configureOwnedConversation();
        ConversationRound round = completedRound();
        round.setStatus(ConversationRoundStatus.FAILED);
        Mockito.when(conversationRoundMapper.getRound(CONVERSATION_ID, 3L)).thenReturn(round);

        RoundPersistenceException error = Assertions.assertThrows(
            RoundPersistenceException.class,
            () -> conversationRoundService.supersedeGeneratedOutputs(USER_ID, CONVERSATION_ID, 3));

        Assertions.assertEquals(
            ConversationErrorCode.CONVERSATION_ERROR_CODE_INVALID_REQUEST_VALUE,
            error.getCode());
        Mockito.verifyNoInteractions(conversationRoundGeneratedFileMapper, fileResourceMapper);
    }

    /** Marks every active relation once while preserving the Round and file-resource records. */
    @Test
    void supersedesActiveRelationsWithoutDeletingFiles()
    {
        configureOwnedConversation();
        ConversationRound round = completedRound();
        Mockito.when(conversationRoundMapper.getRound(CONVERSATION_ID, 3L)).thenReturn(round);
        Mockito.when(conversationRoundGeneratedFileMapper.markAllSuperseded(31L, USER_ID)).thenReturn(2);

        int supersededCount = conversationRoundService.supersedeGeneratedOutputs(
            USER_ID, CONVERSATION_ID, 3);

        Assertions.assertEquals(2, supersededCount);
        Mockito.verify(conversationRoundGeneratedFileMapper).markAllSuperseded(31L, USER_ID);
        Mockito.verifyNoInteractions(fileResourceMapper);
    }

    /** A repeated mutation is idempotent when no active relation remains. */
    @Test
    void repeatedSupersedeReturnsZeroWithoutDeletingFiles()
    {
        configureOwnedConversation();
        ConversationRound round = completedRound();
        Mockito.when(conversationRoundMapper.getRound(CONVERSATION_ID, 3L)).thenReturn(round);
        Mockito.when(conversationRoundGeneratedFileMapper.markAllSuperseded(31L, USER_ID)).thenReturn(0);

        int supersededCount = conversationRoundService.supersedeGeneratedOutputs(
            USER_ID, CONVERSATION_ID, 3);

        Assertions.assertEquals(0, supersededCount);
        Mockito.verifyNoInteractions(fileResourceMapper);
    }

    /** Configures the lock and synchronous transaction used by every valid request test. */
    private void configureTransactionBoundary()
    {
        Mockito.when(conversationMutationLock.acquire(CONVERSATION_ID)).thenReturn(lockHandle);
        Mockito.when(transactionTemplate.execute(ArgumentMatchers.any())).thenAnswer(invocation ->
        {
            TransactionCallback<?> callback = invocation.getArgument(0);

            return callback.doInTransaction(Mockito.mock(TransactionStatus.class));
        });
    }

    /** Configures a successfully locked owner Conversation. */
    private void configureOwnedConversation()
    {
        configureTransactionBoundary();
        Mockito.when(conversationMapper.lockConversationByIdAndUser(CONVERSATION_ID, USER_ID))
            .thenReturn(new Conversation());
    }

    /** Creates the completed source Round used by successful mutation tests.
     * @return active completed Round with a stable database identity
     */
    private ConversationRound completedRound()
    {
        ConversationRound round = new ConversationRound();
        round.setId(31L);
        round.setConversationId(CONVERSATION_ID);
        round.setRoundNumber(3L);
        round.setStatus(ConversationRoundStatus.COMPLETED);

        return round;
    }
}
