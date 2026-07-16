package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationLlmResponseToolCall;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConversationLlmResponseToolCallMapper
{
    ConversationLlmResponseToolCall insertResponseToolCall(ConversationLlmResponseToolCall toolCall);
}
