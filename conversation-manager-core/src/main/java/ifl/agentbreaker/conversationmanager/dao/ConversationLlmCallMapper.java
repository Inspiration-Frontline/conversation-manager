package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationLlmCall;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ConversationLlmCallMapper
{
    ConversationLlmCall insertLlmCall(ConversationLlmCall llmCall);

    ConversationLlmCall getLlmCallByTurnId(@Param("turnId") long turnId);
}
