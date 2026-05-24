package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationGroupRelation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;

@Mapper
public interface ConversationGroupRelationMapper
{
    int insertConversationGroupRelation(ConversationGroupRelation relation);

    int upsertConversationGroupRelation(ConversationGroupRelation relation);

    int deleteConversationGroupRelations(@Param("groupId") String groupId, @Param("userId") long userId, @Param("conversationIds") Collection<String> conversationIds);

    int deleteConversationGroupRelationsByGroupId(@Param("groupId") String groupId, @Param("userId") long userId);

    int deletePinnedConversationRelations(@Param("userId") long userId);

    int insertPinnedConversationRelation(ConversationGroupRelation relation);
}
