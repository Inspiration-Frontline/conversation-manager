package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.dtos.requests.TaskAgentExecutionTerminalUpdate;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.RoundSubExecutionHistory;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationTaskAgentExecution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** MyBatis operations for bounded Task-Agent execution diagnostics. */
@Mapper
public interface ConversationTaskAgentExecutionMapper
{
    /** Inserts one execution detail row.
     * @param execution validated execution metadata
     * @return inserted row with generated identity
     */
    ConversationTaskAgentExecution insertExecution(ConversationTaskAgentExecution execution);

    /** Links a persisted Turn to a Task-Agent execution.
     * @param creatorId owner identity
     * @param executionId execution row identity
     * @param turnId persisted Turn identity
     * @param turnOrder zero-based execution order
     * @return affected row count
     */
    int insertTurnLink(@Param("creatorId") long creatorId,
                       @Param("executionId") long executionId,
                       @Param("turnId") long turnId,
                       @Param("turnOrder") int turnOrder);

    /** Updates terminal state and bounded error/provider fields.
     * @param update terminal values for one owned execution row
     * @return affected row count
     */
    int updateTerminal(TaskAgentExecutionTerminalUpdate update);

    /** Lists the Task-Agent executions of one Conversation in execution order.
     * @param conversationId owned Conversation identifier
     * @return sub-execution projections ordered by Round, Turn, and insertion order
     */
    List<RoundSubExecutionHistory> listRoundTaskAgentExecutions(@Param("conversationId") String conversationId);
}
