package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationTurn;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** MyBatis persistence operations for the model-invocation Turns inside one Round. */
@Mapper
public interface ConversationTurnMapper
{
    /**
     * Inserts ordered Turn aggregates and returns their generated database identities.
     *
     * @param turns validated Turns in contiguous Round order
     * @return inserted Turns carrying generated IDs and persisted request-snapshot hashes
     */
    List<ConversationTurn> insertTurns(@Param("items") List<ConversationTurn> turns);

    /**
     * Loads one completed Turn used to validate a final-answer source boundary.
     *
     * @param roundId database identity of the containing Round
     * @param turnNumber one-based Turn number within the Round
     * @return completed Turn, or {@code null} when absent or non-terminal
     */
    ConversationTurn getCompletedTurn(@Param("roundId") long roundId, @Param("turnNumber") long turnNumber);

    /**
     * Counts persisted Turns to enforce append-only contiguous numbering.
     *
     * @param roundId database identity of the containing Round
     * @return current persisted Turn count
     */
    long countTurns(@Param("roundId") long roundId);
}
