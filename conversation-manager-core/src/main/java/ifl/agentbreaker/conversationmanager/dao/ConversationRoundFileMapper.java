package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.dtos.responses.RoundFileHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ConversationRoundFileMapper
{
    int insertRoundFiles(@Param("roundId") long roundId,
                         @Param("userId") long userId,
                         @Param("fileResourceIds") Collection<Long> fileResourceIds);

    List<RoundFileHistory> listRoundFiles(@Param("conversationId") String conversationId);

    List<Long> listFileResourceIdsByConversation(@Param("conversationId") String conversationId,
                                                 @Param("userId") long userId);

    int deleteByConversationId(@Param("conversationId") String conversationId,
                               @Param("userId") long userId);
}
