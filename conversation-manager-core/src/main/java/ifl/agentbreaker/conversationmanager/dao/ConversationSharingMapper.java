package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationSharing;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ConversationSharingMapper
{
    int insertConversationSharing(ConversationSharing sharing);

    ConversationSharing getConversationSharingBySharedId(@Param("sharedConversationId") String sharedConversationId);

    ConversationSharing getActiveConversationSharingBySharedId(@Param("sharedConversationId") String sharedConversationId);

    List<ConversationSharing> listConversationSharingsByParentId(@Param("parentConversationId") String parentConversationId,
                                                                  @Param("userId") long userId);

    int revokeConversationSharing(@Param("sharedConversationId") String sharedConversationId,
                                  @Param("userId") long userId);

    int revokeByParentConversationIds(@Param("conversationIds") Collection<String> conversationIds,
                                      @Param("userId") long userId);
}
