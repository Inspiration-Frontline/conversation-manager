package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationTurn;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.RoundAssistantAnswerHistory;
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
     * Loads the latest persisted Turn regardless of terminal status.
     *
     * @param roundId database identity of the containing Round
     * @return latest Turn, or {@code null} when the Round has no model invocation
     */
    ConversationTurn getLatestTurn(@Param("roundId") long roundId);

    /**
     * Lists the latest non-empty assistant response for every active Round in one Conversation.
     *
     * @param conversationId stable Conversation identifier
     * @return ordered Round numbers paired with their latest persisted assistant text
     */
    List<RoundAssistantAnswerHistory> listLatestRoundAnswers(
        @Param("conversationId") String conversationId);

    /**
     * Counts persisted Turns to enforce append-only contiguous numbering.
     *
     * @param roundId database identity of the containing Round
     * @return current persisted Turn count
     */
    long countTurns(@Param("roundId") long roundId);
}
