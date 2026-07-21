package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationRound;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ConversationRoundMapper
{
    ConversationRound insertRound(ConversationRound round);

    ConversationRound getRound(@Param("conversationId") String conversationId,
                               @Param("roundNumber") long roundNumber);

    List<ConversationRound> listActiveRounds(@Param("conversationId") String conversationId);

    List<ConversationRound> listCompletedRoundsAtOrBefore(@Param("conversationId") String conversationId,
                                                           @Param("endRoundNumber") long endRoundNumber);

    ConversationRound getLatestCompletedRoundAtOrBefore(@Param("conversationId") String conversationId,
                                                         @Param("endRoundNumber") long endRoundNumber);

    int forkConversationHistory(@Param("sourceConversationId") String sourceConversationId,
                                @Param("targetConversationId") String targetConversationId,
                                @Param("userId") long userId,
                                @Param("endRoundNumber") long endRoundNumber);

}
