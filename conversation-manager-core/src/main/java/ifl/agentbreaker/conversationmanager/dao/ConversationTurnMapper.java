package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationTurn;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ConversationTurnMapper
{
    ConversationTurn insertTurn(ConversationTurn turn);

    ConversationTurn getCompletedTurn(@Param("roundId") long roundId, @Param("turnNumber") long turnNumber);
}
