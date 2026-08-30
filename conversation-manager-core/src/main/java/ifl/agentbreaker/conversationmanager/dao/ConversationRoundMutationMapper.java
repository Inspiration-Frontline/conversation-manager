package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationRoundMutation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** MyBatis persistence operations for the immutable Round mutation idempotency ledger. */
@Mapper
public interface ConversationRoundMutationMapper
{
    /**
     * Finds a previously committed mutation so an identical transport retry can replay its result.
     *
     * @param roundId database identity of the mutated Round
     * @param mutationId caller-supplied logical command identity
     * @return committed mutation, or {@code null} when this command has not run
     */
    ConversationRoundMutation getMutation(@Param("roundId") long roundId, @Param("mutationId") String mutationId);

    /**
     * Records one successfully committed Round mutation.
     *
     * @param mutation immutable ledger row with payload hash and committed revision
     * @return affected row count
     */
    int insertMutation(ConversationRoundMutation mutation);
}
