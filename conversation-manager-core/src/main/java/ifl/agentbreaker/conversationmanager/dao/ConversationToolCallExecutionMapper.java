package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.dtos.responses.RoundToolActivityHistory;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationToolCallExecution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** MyBatis persistence operations for model-emitted Tool calls and their terminal executions. */
@Mapper
public interface ConversationToolCallExecutionMapper
{
    /**
     * Persists a batch of complete Tool-call audit records across already-persisted Turns.
     *
     * @param executions executions carrying model call identity, arguments, result, status, and timing
     * @return affected row count
     */
    int insertToolCallExecutions(@Param("items") List<ConversationToolCallExecution> executions);

    /**
     * Loads all visible Tool activities for one Conversation in display order.
     *
     * @param conversationId stable Conversation identifier
     * @return activities ordered by Round, Turn, and execution position
     */
    List<RoundToolActivityHistory> listRoundToolActivities(@Param("conversationId") String conversationId);
}
