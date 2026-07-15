package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationLlmRequestMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConversationLlmRequestMessageMapper
{
    ConversationLlmRequestMessage insertRequestMessage(ConversationLlmRequestMessage message);
}
