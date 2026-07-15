package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationTurn;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConversationTurnMapper
{
    ConversationTurn insertTurn(ConversationTurn turn);
}
