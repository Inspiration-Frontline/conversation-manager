package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ConversationMessageMapper
{
    int insertMessage(ConversationMessage message);

    int insertMessages(@Param("messages") Collection<ConversationMessage> messages);

    int forkConversationMessages(@Param("sourceConversationId") String sourceConversationId,
                                 @Param("targetConversationId") String targetConversationId,
                                 @Param("userId") long userId,
                                 @Param("endMessageId") long endMessageId);

    List<ConversationMessage> listConversationMessages(@Param("conversationId") String conversationId, @Param("endMessageId") Long endMessageId);

    long getMaxMessageId(@Param("conversationId") String conversationId);

    int deleteMessages(@Param("conversationId") String conversationId, @Param("userId") long userId, @Param("messageIds") Collection<Long> messageIds);
}
