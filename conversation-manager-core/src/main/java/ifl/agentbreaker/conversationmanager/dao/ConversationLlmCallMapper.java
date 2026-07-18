package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationLlmCall;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ConversationLlmCallMapper
{
    List<ConversationLlmCall> insertLlmCalls(@Param("items") List<ConversationLlmCall> llmCalls);

    ConversationLlmCall getLlmCallByTurnId(@Param("turnId") long turnId);
}
