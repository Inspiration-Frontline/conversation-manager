package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationLlmRequestMessageToolCall;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** MyBatis persistence operations for historical assistant Tool calls embedded in request messages. */
@Mapper
public interface ConversationLlmRequestMessageToolCallMapper
{
    /**
     * Persists assistant Tool calls after their parent request-message IDs are known.
     *
     * @param toolCalls ordered Tool-call rows belonging to normalized assistant messages
     * @return affected row count
     */
    int insertRequestMessageToolCalls(
        @Param("items") List<ConversationLlmRequestMessageToolCall> toolCalls);

    /**
     * Loads all historical assistant Tool calls needed to reconstruct one Round's model inputs.
     *
     * @param roundId database identity of the containing Round
     * @return Tool calls ordered by Turn, message, and call position
     */
    List<ConversationLlmRequestMessageToolCall> listRequestMessageToolCallsForRound(
        @Param("roundId") long roundId);
}
