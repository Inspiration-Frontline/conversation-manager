package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationTaskAgentExecution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
     * @param executionId execution identity
     * @param userId authenticated owner
     * @param status terminal status
     * @param providerRequestId bounded provider request identifier
     * @param errorCode safe failure classification
     * @param errorMessage safe failure message
     * @param endTime terminal time
     * @return affected row count
     */
    int updateTerminal(@Param("executionId") long executionId,
                       @Param("userId") long userId,
                       @Param("status") String status,
                       @Param("providerRequestId") String providerRequestId,
                       @Param("errorCode") String errorCode,
                       @Param("errorMessage") String errorMessage,
                       @Param("endTime") java.time.Instant endTime);
}
