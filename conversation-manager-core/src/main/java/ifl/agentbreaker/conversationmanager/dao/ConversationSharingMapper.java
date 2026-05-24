package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationSharing;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ConversationSharingMapper
{
    int insertConversationSharing(ConversationSharing sharing);

    ConversationSharing getConversationSharingBySharedId(@Param("sharedConversationId") String sharedConversationId);
}
