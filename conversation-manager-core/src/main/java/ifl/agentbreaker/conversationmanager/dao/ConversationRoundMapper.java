package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.dtos.ConversationReferenceBoundary;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationRound;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.time.Instant;
import java.util.List;

@Mapper
public interface ConversationRoundMapper
{
    ConversationRound insertRound(ConversationRound round);

    ConversationRound insertCheckpoint(ConversationRound round);

    /**
     * Advances an in-progress Round revision without changing its terminal fields. Append-progress
     * mutations use this after their append-only Turn or dispatch rows have been inserted.
     *
     * @param roundId database ID of the mutable Round
     * @param expectedRevision caller's optimistic-lock revision
     * @param modifierId authenticated user recorded as the modifier
     * @return {@code 1} when the expected revision matched, otherwise {@code 0}
     */
    int advanceRevision(@Param("roundId") long roundId, @Param("expectedRevision") long expectedRevision,
                        @Param("modifierId") long modifierId);

    /**
     * Advances a mutable Round revision and writes its terminal status, answer, error, and end
     * time atomically. Finalization uses this instead of {@link #advanceRevision(long, long, long)}
     * because it must prevent another append from racing with the terminal state transition.
     *
     * @param roundId database ID of the mutable Round
     * @param expectedRevision caller's optimistic-lock revision
     * @param modifierId authenticated user recorded as the modifier
     * @param status terminal Round status
     * @param finalAnswerContent nullable final text answer
     * @param finalAnswerContentParts nullable structured final answer parts
     * @param finalSourceTurnNumber nullable source Turn that produced the answer
     * @param errorMessage nullable terminal failure or cancellation reason
     * @param endTime terminal completion time
     * @return {@code 1} when finalization committed, otherwise {@code 0}
     */
    int finalizeRound(@Param("roundId") long roundId, @Param("expectedRevision") long expectedRevision,
                      @Param("modifierId") long modifierId, @Param("status") String status,
                      @Param("finalAnswerContent") String finalAnswerContent,
                      @Param("finalAnswerContentParts") String finalAnswerContentParts,
                      @Param("finalSourceTurnNumber") Long finalSourceTurnNumber,
                      @Param("errorMessage") String errorMessage, @Param("endTime") Instant endTime);

    ConversationRound getRound(@Param("conversationId") String conversationId,
                               @Param("roundNumber") long roundNumber);

    List<ConversationRound> listActiveRounds(@Param("conversationId") String conversationId);

    List<ConversationRound> listCompletedRoundsAtOrBefore(@Param("conversationId") String conversationId,
                                                           @Param("endRoundNumber") long endRoundNumber);

    List<ConversationRound> listRoundsAtBoundaries(
        @Param("boundaries") Collection<ConversationReferenceBoundary> boundaries);

    List<ConversationRound> listCompletedRoundsAtOrBeforeBoundaries(
        @Param("boundaries") Collection<ConversationReferenceBoundary> boundaries);

    List<String> listConversationIdsWithCompletedRounds(
        @Param("conversationIds") Collection<String> conversationIds);

    ConversationRound getLatestCompletedRoundAtOrBefore(@Param("conversationId") String conversationId,
                                                         @Param("endRoundNumber") long endRoundNumber);

    int forkConversationHistory(@Param("sourceConversationId") String sourceConversationId,
                                @Param("targetConversationId") String targetConversationId,
                                @Param("userId") long userId,
                                @Param("endRoundNumber") long endRoundNumber);

    int copyForkedRoundTraceIds(@Param("sourceConversationId") String sourceConversationId,
                                @Param("targetConversationId") String targetConversationId,
                                @Param("endRoundNumber") long endRoundNumber);

}
