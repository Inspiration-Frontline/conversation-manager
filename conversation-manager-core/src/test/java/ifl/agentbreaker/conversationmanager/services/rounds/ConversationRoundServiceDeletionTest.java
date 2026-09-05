package ifl.agentbreaker.conversationmanager.services.rounds;

import ifl.agentbreaker.conversationmanager.dao.ConversationMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationRoundMapper;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.RoundDeletionResult;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.Conversation;
import ifl.agentbreaker.conversationmanager.rpc.ConversationErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

/** Verifies tail-only retry deletion inside the consolidated Round service. */
@ExtendWith(MockitoExtension.class)
class ConversationRoundServiceDeletionTest
{
    /** Stable owner used by every deletion assertion. */
    private static final long USER_ID = 7L;

    /** Parent Conversation mapper used for ownership checks. */
    @Mock
    private ConversationMapper conversationMapper;

    /** Round mapper used for the active suffix read and set-based tombstone. */
    @Mock
    private ConversationRoundMapper conversationRoundMapper;

    /** Aggregate mutation lock shared with persistence. */
    @Mock
    private ConversationMutationLock conversationMutationLock;

    /** Lock handle released after each service operation. */
    @Mock
    private ConversationMutationLock.LockHandle lockHandle;

    /** Transaction enclosing ownership, suffix validation, and tombstoning. */
    @Mock
    private TransactionTemplate transactionTemplate;

    /** Consolidated Round service under test. */
    @InjectMocks
    private ConversationRoundService conversationRoundService;

    /** Configures the aggregate lock and executes transaction callbacks synchronously. */
    @BeforeEach
    void configureTransactionBoundary()
    {
        Mockito.when(conversationMutationLock.acquire("conv_delete")).thenReturn(lockHandle);
        Mockito.when(transactionTemplate.execute(ArgumentMatchers.any())).thenAnswer(invocation ->
        {
            TransactionCallback<?> callback = invocation.getArgument(0);

            return callback.doInTransaction(Mockito.mock(TransactionStatus.class));
        });
        Mockito.when(conversationMapper.lockConversationByIdAndUser("conv_delete", USER_ID))
            .thenReturn(new Conversation());
    }

    /** Confirms a contiguous active suffix is deleted once in descending response order. */
    @Test
    void deletesOnlyTheContiguousActiveTailWithOneSetBasedWrite()
    {
        Mockito.when(conversationRoundMapper.listActiveRoundNumbers("conv_delete"))
            .thenReturn(List.of(1L, 2L, 3L));
        Mockito.when(conversationRoundMapper.tombstoneRounds("conv_delete", List.of(3L, 2L), USER_ID))
            .thenReturn(2);

        RoundDeletionResult result = conversationRoundService.deleteRounds(
            USER_ID, "conv_delete", List.of(2L, 3L));

        Assertions.assertEquals(List.of(3L, 2L), result.deletedRoundNumbers());
        Assertions.assertEquals(List.of(), result.failures());
        Mockito.verify(conversationRoundMapper).tombstoneRounds("conv_delete", List.of(3L, 2L), USER_ID);
    }

    /** Confirms deleting a non-tail Round is rejected before the set-based write. */
    @Test
    void rejectsDeletionThatDoesNotEndAtTheLatestActiveRound()
    {
        Mockito.when(conversationRoundMapper.listActiveRoundNumbers("conv_delete"))
            .thenReturn(List.of(1L, 2L, 3L));

        RoundPersistenceException error = Assertions.assertThrows(
            RoundPersistenceException.class,
            () -> conversationRoundService.deleteRounds(USER_ID, "conv_delete", List.of(2L)));

        Assertions.assertEquals(
            ConversationErrorCode.CONVERSATION_ERROR_CODE_DELETE_REQUIRES_TAIL_SUFFIX_VALUE,
            error.getCode());
        Mockito.verify(conversationRoundMapper, Mockito.never()).tombstoneRounds(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.anyLong());
    }
}
