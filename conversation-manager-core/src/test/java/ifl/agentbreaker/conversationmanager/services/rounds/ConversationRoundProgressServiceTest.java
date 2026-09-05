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

import org.junit.jupiter.api.Assertions;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

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
        Mockito.when(mutationLock.acquire("conv_progress")).thenReturn(lockHandle);
        Mockito.when(transactionTemplate.execute(ArgumentMatchers.any())).thenAnswer(invocation ->
        {
            TransactionCallback<?> callback = invocation.getArgument(0);

            return callback.doInTransaction(Mockito.mock(TransactionStatus.class));
        });
    }

    @Test
    void appendsDispatchEvidenceOnceAndReplaysTheSameMutationIdempotently()
    {
        AppendConversationRoundProgressRequest request = dispatchRequest(1L, "mutation-1");
        ConversationRound round = inProgressRound(42L, 1L);
        ConversationToolDispatch dispatch = new ConversationToolDispatch();
        AtomicReference<ConversationRoundMutation> recorded = new AtomicReference<>();

        Mockito.when(conversationMapper.lockConversationByIdAndUser("conv_progress", 7L)).thenReturn(new Conversation());
        Mockito.when(roundMapper.getRound("conv_progress", 1L)).thenReturn(round);
        Mockito.when(mutationMapper.getMutation(42L, "mutation-1")).thenAnswer(invocation -> recorded.get());
        Mockito.when(progressMapper.toDispatches(7L, 42L, request.getDispatchEvidenceList())).thenReturn(List.of(dispatch));
        Mockito.when(dispatchMapper.upsertDispatchEvidence(List.of(dispatch))).thenReturn(1);
        Mockito.when(roundMapper.advanceRevision(42L, 1L, 7L)).thenReturn(1);
        Mockito.when(mutationMapper.insertMutation(ArgumentMatchers.any())).thenAnswer(invocation ->
        {
            recorded.set(invocation.getArgument(0));

            return 1;
        });

        ConversationRoundProgressService.MutationOutcome first = progressService.append(request);
        ConversationRoundProgressService.MutationOutcome replay = progressService.append(request);

        Assertions.assertEquals(2L, first.revision());
        Assertions.assertTrue(replay.idempotentReplay());
        Assertions.assertEquals(first.revision(), replay.revision());
        Mockito.verify(dispatchMapper, Mockito.times(1)).upsertDispatchEvidence(List.of(dispatch));
        Mockito.verify(roundMapper, Mockito.times(1)).advanceRevision(42L, 1L, 7L);
        Mockito.verify(mutationMapper, Mockito.times(1)).insertMutation(ArgumentMatchers.any());
    }

    @Test
    void rejectsAStaleRevisionBeforeWritingDispatchEvidence()
    {
        AppendConversationRoundProgressRequest request = dispatchRequest(1L, "mutation-stale");
        ConversationRound round = inProgressRound(42L, 2L);
        Mockito.when(conversationMapper.lockConversationByIdAndUser("conv_progress", 7L)).thenReturn(new Conversation());
        Mockito.when(roundMapper.getRound("conv_progress", 1L)).thenReturn(round);

        RoundPersistenceException error = Assertions.assertThrows(
            RoundPersistenceException.class,
            () -> progressService.append(request));

        Assertions.assertEquals("expected_revision does not match the committed Round revision.", error.getMessage());
        Mockito.verify(progressMapper, Mockito.never()).toDispatches(ArgumentMatchers.anyLong(), ArgumentMatchers.anyLong(), ArgumentMatchers.any());
        Mockito.verify(dispatchMapper, Mockito.never()).upsertDispatchEvidence(ArgumentMatchers.any());
        Mockito.verify(roundMapper, Mockito.never()).advanceRevision(ArgumentMatchers.anyLong(), ArgumentMatchers.anyLong(), ArgumentMatchers.anyLong());
    }

    private AppendConversationRoundProgressRequest dispatchRequest(long expectedRevision, String mutationId)
    {
        return AppendConversationRoundProgressRequest.newBuilder()
            .setUserId(7L)
            .setConversationId("conv_progress")
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
