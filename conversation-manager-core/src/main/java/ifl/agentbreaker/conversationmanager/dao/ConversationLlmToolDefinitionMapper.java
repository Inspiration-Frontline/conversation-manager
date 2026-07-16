package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationLlmToolDefinition;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConversationLlmToolDefinitionMapper
{
    ConversationLlmToolDefinition insertToolDefinition(ConversationLlmToolDefinition definition);
}
