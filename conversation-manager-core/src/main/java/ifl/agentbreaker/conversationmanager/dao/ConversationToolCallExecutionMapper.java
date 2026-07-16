package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationToolCallExecution;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConversationToolCallExecutionMapper
{
    ConversationToolCallExecution insertToolCallExecution(ConversationToolCallExecution execution);
}
