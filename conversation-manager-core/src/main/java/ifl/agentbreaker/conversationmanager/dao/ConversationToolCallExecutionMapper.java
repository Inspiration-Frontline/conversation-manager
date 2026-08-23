package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.dtos.responses.RoundToolActivityHistory;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationToolCallExecution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ConversationToolCallExecutionMapper
{
    int insertToolCallExecutions(@Param("items") List<ConversationToolCallExecution> executions);

    /**
     * Loads all visible Tool activities for one Conversation in display order.
     *
     * @param conversationId stable Conversation identifier
     * @return activities ordered by Round, Turn, and execution position
     */
    List<RoundToolActivityHistory> listRoundToolActivities(@Param("conversationId") String conversationId);
}
