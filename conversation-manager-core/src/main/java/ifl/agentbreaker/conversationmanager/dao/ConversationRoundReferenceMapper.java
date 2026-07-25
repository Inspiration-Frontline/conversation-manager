package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationRoundReference;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ConversationRoundReferenceMapper
{
    int insertReferences(List<ConversationRoundReference> references);

    List<ConversationRoundReference> listReferencesByRoundId(long roundId);

    List<ConversationRoundReference> listReferencesByRoundIds(@Param("roundIds") List<Long> roundIds);
}
