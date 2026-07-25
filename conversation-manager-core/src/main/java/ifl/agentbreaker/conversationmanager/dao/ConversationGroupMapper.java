package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationGroup;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.ConversationGroupAbstract;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ConversationGroupMapper
{
    int insertConversationGroup(ConversationGroup group);

    ConversationGroup getConversationGroupByIdForUser(@Param("groupId") long groupId, @Param("userId") long userId);

    ConversationGroup lockConversationGroupByIdForUser(@Param("groupId") long groupId, @Param("userId") long userId);

    boolean existsByIdAndUser(@Param("groupId") long groupId, @Param("userId") long userId);

    int updateConversationGroupAbstract(ConversationGroup group);

    int deleteConversationGroup(@Param("groupId") long groupId, @Param("userId") long userId);

    List<ConversationGroupAbstract> listConversationGroups(@Param("userId") long userId);

    int batchUpdateConversationGroupSortOrders(@Param("groups") List<ConversationGroup> groups);

    int incrementConversationGroupSortOrders(@Param("userId") long userId);

    List<ConversationGroup> listConversationGroupsForUpdate(@Param("userId") long userId);

    void acquireUserGroupLock(@Param("userId") long userId);
}
