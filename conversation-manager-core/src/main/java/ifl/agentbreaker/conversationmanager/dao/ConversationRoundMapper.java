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

    int advanceRevision(@Param("roundId") long roundId, @Param("expectedRevision") long expectedRevision,
                        @Param("modifierId") long modifierId);

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
