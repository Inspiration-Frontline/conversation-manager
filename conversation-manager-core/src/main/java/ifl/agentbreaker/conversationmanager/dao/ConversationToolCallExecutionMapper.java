package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationToolCallExecution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ConversationToolCallExecutionMapper
{
    int insertToolCallExecutions(@Param("items") List<ConversationToolCallExecution> executions);
}
