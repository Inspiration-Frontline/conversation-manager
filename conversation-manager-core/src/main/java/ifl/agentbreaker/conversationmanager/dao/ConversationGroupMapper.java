package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ConversationGroupMapper
{
    int insertConversationGroup(ConversationGroup group);

    ConversationGroup getConversationGroupByIdForUser(@Param("groupId") String groupId, @Param("userId") long userId);

    ConversationGroup lockConversationGroupByIdForUser(@Param("groupId") String groupId, @Param("userId") long userId);

    boolean existsByIdAndUser(@Param("groupId") String groupId, @Param("userId") long userId);

    int updateConversationGroupAbstract(ConversationGroup group);

    int deleteConversationGroup(@Param("groupId") String groupId, @Param("userId") long userId);

    List<ConversationGroup> listConversationGroups(@Param("userId") long userId);

    int updateConversationGroupSortOrder(@Param("groupId") String groupId, @Param("userId") long userId, @Param("sortOrder") int sortOrder);

    int incrementConversationGroupSortOrders(@Param("userId") long userId);

    List<String> listConversationGroupIdsForUpdate(@Param("userId") long userId);

    void acquireUserGroupLock(@Param("userId") long userId);
}
