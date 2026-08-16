package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationRoundMutation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ConversationRoundMutationMapper
{
    ConversationRoundMutation getMutation(@Param("roundId") long roundId, @Param("mutationId") String mutationId);

    int insertMutation(ConversationRoundMutation mutation);
}
