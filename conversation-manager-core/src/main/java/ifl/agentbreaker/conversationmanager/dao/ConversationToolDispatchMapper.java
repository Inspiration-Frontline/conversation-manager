package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationToolDispatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

@Mapper
public interface ConversationToolDispatchMapper
{
    int upsertDispatchEvidence(@Param("items") List<ConversationToolDispatch> items);

    int recoverStaleDispatches(@Param("recoveryTime") Instant recoveryTime,
                               @Param("reason") String reason);
}
