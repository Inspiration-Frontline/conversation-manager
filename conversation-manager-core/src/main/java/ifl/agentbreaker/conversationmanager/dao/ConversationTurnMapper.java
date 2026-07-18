package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationTurn;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ConversationTurnMapper
{
    List<ConversationTurn> insertTurns(@Param("items") List<ConversationTurn> turns);

    ConversationTurn getCompletedTurn(@Param("roundId") long roundId, @Param("turnNumber") long turnNumber);
}
