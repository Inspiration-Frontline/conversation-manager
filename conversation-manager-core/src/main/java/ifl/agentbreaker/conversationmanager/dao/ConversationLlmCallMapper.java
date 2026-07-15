package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationLlmCall;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConversationLlmCallMapper
{
    ConversationLlmCall insertLlmCall(ConversationLlmCall llmCall);
}
