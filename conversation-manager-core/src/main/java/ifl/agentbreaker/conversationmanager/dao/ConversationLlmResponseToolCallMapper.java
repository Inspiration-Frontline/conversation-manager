package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationLlmResponseToolCall;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ConversationLlmResponseToolCallMapper
{
    List<ConversationLlmResponseToolCall> insertResponseToolCalls(
        @Param("items") List<ConversationLlmResponseToolCall> toolCalls);
}
