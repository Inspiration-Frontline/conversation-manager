package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationLlmToolDefinition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ConversationLlmToolDefinitionMapper
{
    int insertToolDefinitions(@Param("items") List<ConversationLlmToolDefinition> definitions);
}
