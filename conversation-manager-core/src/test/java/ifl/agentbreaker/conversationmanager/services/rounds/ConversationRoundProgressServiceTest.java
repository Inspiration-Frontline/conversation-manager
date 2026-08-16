package ifl.agentbreaker.conversationmanager.services.rounds;

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
import ifl.agentbreaker.conversationmanager.rpc.ToolDispatchEvidence;
import ifl.agentbreaker.conversationmanager.rpc.ToolDispatchState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationRoundProgressServiceTest
{
    @Mock private ConversationMapper conversationMapper;
    @Mock private ConversationRoundMapper roundMapper;
    @Mock private ConversationRoundMutationMapper mutationMapper;
    @Mock private ConversationToolDispatchMapper dispatchMapper;
    @Mock private ConversationTurnMapper turnMapper;
    @Mock private ConversationRoundService roundService;
    @Mock private ConversationMutationLock mutationLock;
    @Mock private ConversationMutationLock.LockHandle lockHandle;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private ConversationRoundProgressMapper progressMapper;
    @Spy private ConversationRoundProgressValidator validator = new ConversationRoundProgressValidator();

    @InjectMocks private ConversationRoundProgressService progressService;

    @BeforeEach
    void executeTransactionCallbacks()
    {
        when(mutationLock.acquire("conv_phase12")).thenReturn(lockHandle);
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
        {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
    }

    @Test
    void appendsDispatchEvidenceOnceAndReplaysTheSameMutationIdempotently()
    {
        AppendConversationRoundProgressRequest request = dispatchRequest(1L, "mutation-1");
        ConversationRound round = inProgressRound(42L, 1L);
        ConversationToolDispatch dispatch = new ConversationToolDispatch();
        AtomicReference<ConversationRoundMutation> recorded = new AtomicReference<>();

        when(conversationMapper.lockConversationByIdAndUser("conv_phase12", 7L)).thenReturn(new Conversation());
        when(roundMapper.getRound("conv_phase12", 1L)).thenReturn(round);
        when(mutationMapper.getMutation(42L, "mutation-1")).thenAnswer(invocation -> recorded.get());
        when(progressMapper.toDispatches(42L, request.getDispatchEvidenceList())).thenReturn(List.of(dispatch));
        when(dispatchMapper.upsertDispatchEvidence(List.of(dispatch))).thenReturn(1);
        when(roundMapper.advanceRevision(42L, 1L, 7L)).thenReturn(1);
        when(mutationMapper.insertMutation(any())).thenAnswer(invocation ->
        {
            recorded.set(invocation.getArgument(0));
            return 1;
        });

        ConversationRoundProgressService.MutationOutcome first = progressService.append(request);
        ConversationRoundProgressService.MutationOutcome replay = progressService.append(request);

        assertEquals(2L, first.revision());
        assertTrue(replay.idempotentReplay());
        assertEquals(first.revision(), replay.revision());
        verify(dispatchMapper, times(1)).upsertDispatchEvidence(List.of(dispatch));
        verify(roundMapper, times(1)).advanceRevision(42L, 1L, 7L);
        verify(mutationMapper, times(1)).insertMutation(any());
    }

    @Test
    void rejectsAStaleRevisionBeforeWritingDispatchEvidence()
    {
        AppendConversationRoundProgressRequest request = dispatchRequest(1L, "mutation-stale");
        ConversationRound round = inProgressRound(42L, 2L);
        when(conversationMapper.lockConversationByIdAndUser("conv_phase12", 7L)).thenReturn(new Conversation());
        when(roundMapper.getRound("conv_phase12", 1L)).thenReturn(round);

        RoundPersistenceException error = assertThrows(
            RoundPersistenceException.class,
            () -> progressService.append(request));

        assertEquals("expected_revision does not match the committed Round revision.", error.getMessage());
        verify(progressMapper, never()).toDispatches(any(Long.class), any());
        verify(dispatchMapper, never()).upsertDispatchEvidence(any());
        verify(roundMapper, never()).advanceRevision(any(Long.class), any(Long.class), any(Long.class));
    }

    private AppendConversationRoundProgressRequest dispatchRequest(long expectedRevision, String mutationId)
    {
        return AppendConversationRoundProgressRequest.newBuilder()
            .setUserId(7L)
            .setConversationId("conv_phase12")
            .setRoundNumber(1L)
            .setMutationId(mutationId)
            .setExpectedRevision(expectedRevision)
            .addDispatchEvidence(ToolDispatchEvidence.newBuilder()
                .setAttemptId("attempt-1")
                .setTurnNumber(2L)
                .setToolCallId("call-real-1")
                .setToolName("echo")
                .setToolKey("mcp.fixture.echo")
                .setServerId("fixture")
                .setArgumentsJson("{}")
                .setState(ToolDispatchState.TOOL_DISPATCH_STATE_DISPATCHING)
                .setDispatchTime(1L)
                .build())
            .build();
    }

    private ConversationRound inProgressRound(long roundId, long revision)
    {
        ConversationRound round = new ConversationRound();
        round.setId(roundId);
        round.setStatus(ConversationRoundStatus.IN_PROGRESS);
        round.setRevision(revision);
        return round;
    }
}
