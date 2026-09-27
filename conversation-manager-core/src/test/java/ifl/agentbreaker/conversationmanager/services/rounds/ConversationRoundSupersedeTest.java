package ifl.agentbreaker.conversationmanager.services.rounds;

import ifl.agentbreaker.conversationmanager.dao.ConversationRoundMapper;
import ifl.agentbreaker.conversationmanager.domain.constants.ConversationRoundStatus;
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

import java.util.List;

/** Verifies the atomic supersede step performed by a replacement retry checkpoint. */
@ExtendWith(MockitoExtension.class)
class ConversationRoundSupersedeTest
{
    /** Stable owner used by every supersede assertion. */
    private static final long USER_ID = 7L;

    /** Stable Conversation identity used by every supersede assertion. */
    private static final String CONVERSATION_ID = "conv_supersede";

    /** Round mapper used for the active-tail read and set-based tombstone. */
    @Mock
    private ConversationRoundMapper conversationRoundMapper;

    /** Service under test. */
    @InjectMocks
    private ConversationRoundService conversationRoundService;

    /** Confirms the replacement checkpoint tombstones exactly the latest retryable Round. */
    @Test
    void tombstonesLatestRetryableRoundForReplacementCheckpoint()
    {
        ConversationRound failedRound = Mockito.mock(ConversationRound.class);
        Mockito.when(failedRound.getRoundNumber()).thenReturn(1L);
        Mockito.when(failedRound.getStatus()).thenReturn(ConversationRoundStatus.FAILED);
        Mockito.when(conversationRoundMapper.listActiveRounds(CONVERSATION_ID)).thenReturn(List.of(failedRound));
        Mockito.when(conversationRoundMapper.tombstoneRounds(CONVERSATION_ID, List.of(1L), USER_ID)).thenReturn(1);

        conversationRoundService.tombstoneSupersededRound(USER_ID, CONVERSATION_ID, 1L);

        Mockito.verify(conversationRoundMapper).tombstoneRounds(CONVERSATION_ID, List.of(1L), USER_ID);
    }

    /** Confirms a supersede target that is not the active tail is rejected without a write. */
    @Test
    void rejectsSupersededRoundThatIsNotTheActiveTail()
    {
        ConversationRound olderRound = Mockito.mock(ConversationRound.class);
        ConversationRound latestRound = Mockito.mock(ConversationRound.class);
        Mockito.when(latestRound.getRoundNumber()).thenReturn(2L);
        Mockito.when(latestRound.getStatus()).thenReturn(ConversationRoundStatus.FAILED);
        Mockito.when(conversationRoundMapper.listActiveRounds(CONVERSATION_ID))
            .thenReturn(List.of(olderRound, latestRound));

        RoundPersistenceException error = Assertions.assertThrows(
            RoundPersistenceException.class,
            () -> conversationRoundService.tombstoneSupersededRound(USER_ID, CONVERSATION_ID, 1L));

        Assertions.assertEquals(
            ConversationErrorCode.CONVERSATION_ERROR_CODE_DELETE_REQUIRES_TAIL_SUFFIX_VALUE,
            error.getCode());
        Mockito.verify(conversationRoundMapper, Mockito.never())
            .tombstoneRounds(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.anyLong());
    }

    /** Confirms a completed latest Round cannot be superseded by a retry checkpoint. */
    @Test
    void rejectsSupersedingACompletedRound()
    {
        ConversationRound completedRound = Mockito.mock(ConversationRound.class);
        Mockito.when(completedRound.getRoundNumber()).thenReturn(1L);
        Mockito.when(completedRound.getStatus()).thenReturn(ConversationRoundStatus.COMPLETED);
        Mockito.when(conversationRoundMapper.listActiveRounds(CONVERSATION_ID)).thenReturn(List.of(completedRound));

        RoundPersistenceException error = Assertions.assertThrows(
            RoundPersistenceException.class,
            () -> conversationRoundService.tombstoneSupersededRound(USER_ID, CONVERSATION_ID, 1L));

        Assertions.assertEquals(
            ConversationErrorCode.CONVERSATION_ERROR_CODE_DELETE_REQUIRES_TAIL_SUFFIX_VALUE,
            error.getCode());
        Mockito.verify(conversationRoundMapper, Mockito.never())
            .tombstoneRounds(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.anyLong());
    }

    /** Confirms a supersede request without any active Round is rejected. */
    @Test
    void rejectsSupersedingWhenNoActiveRoundExists()
    {
        Mockito.when(conversationRoundMapper.listActiveRounds(CONVERSATION_ID)).thenReturn(List.of());

        RoundPersistenceException error = Assertions.assertThrows(
            RoundPersistenceException.class,
            () -> conversationRoundService.tombstoneSupersededRound(USER_ID, CONVERSATION_ID, 1L));

        Assertions.assertEquals(
            ConversationErrorCode.CONVERSATION_ERROR_CODE_DELETE_REQUIRES_TAIL_SUFFIX_VALUE,
            error.getCode());
        Mockito.verify(conversationRoundMapper, Mockito.never())
            .tombstoneRounds(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.anyLong());
    }
}
