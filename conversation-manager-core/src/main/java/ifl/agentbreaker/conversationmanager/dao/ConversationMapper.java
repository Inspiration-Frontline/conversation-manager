package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.Conversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ConversationMapper
{
    Conversation insertConversation(Conversation conversation);

    Conversation getConversationById(@Param("conversationId") String conversationId);

    Conversation getConversationByIdAndUser(@Param("conversationId") String conversationId, @Param("userId") long userId);

    boolean existsByIdAndUser(@Param("conversationId") String conversationId, @Param("userId") long userId);

    List<Conversation> listConversations(@Param("userId") long userId, @Param("keyword") String keyword, @Param("limit") int limit, @Param("offset") int offset);

    long countConversations(@Param("userId") long userId, @Param("keyword") String keyword);

    Conversation updateConversationTitle(@Param("conversationId") String conversationId, @Param("userId") long userId, @Param("title") String title);

    int deleteConversation(@Param("conversationId") String conversationId, @Param("userId") long userId);

    int deleteConversationsByGroupId(@Param("groupId") String groupId, @Param("userId") long userId);

    boolean allOwnedConversationsExist(@Param("userId") long userId, @Param("conversationIds") Collection<String> conversationIds);
}
