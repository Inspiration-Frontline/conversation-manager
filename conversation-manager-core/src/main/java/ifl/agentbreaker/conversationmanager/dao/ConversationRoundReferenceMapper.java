package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationRoundReference;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** MyBatis persistence operations for frozen Conversation references owned by destination Rounds. */
@Mapper
public interface ConversationRoundReferenceMapper
{
    /**
     * Persists an ordered reference batch after owner and Group validation.
     *
     * @param references references carrying destination Round IDs and frozen source boundaries
     * @return affected row count
     */
    int insertReferences(List<ConversationRoundReference> references);

    /**
     * Loads references for one destination Round.
     *
     * @param roundId database identity of the destination Round
     * @return references in request order
     */
    List<ConversationRoundReference> listReferencesByRoundId(long roundId);

    /**
     * Batch-loads references for multiple destination Rounds without per-Round queries.
     *
     * @param roundIds database identities of destination Rounds
     * @return references grouped by Round and ordered by request position
     */
    List<ConversationRoundReference> listReferencesByRoundIds(@Param("roundIds") List<Long> roundIds);

    /**
     * Tests whether a completed shared boundary contains any private Conversation reference.
     *
     * @param conversationId stable destination Conversation identifier
     * @param endRoundNumber inclusive share or fork boundary
     * @return {@code true} when any selected completed Round contains a reference
     */
    boolean hasReferencesInCompletedRoundsAtOrBefore(
        @Param("conversationId") String conversationId,
        @Param("endRoundNumber") long endRoundNumber);
}
