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

    long countTurns(@Param("roundId") long roundId);
}
