package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationToolDispatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/** MyBatis persistence operations for durable remote Tool delivery attempts. */
@Mapper
public interface ConversationToolDispatchMapper
{
    /**
     * Inserts dispatch intent or advances the same attempt to one terminal delivery state.
     *
     * @param items delivery evidence rows identified by unique attempt IDs
     * @return affected row count; fewer rows means a terminal attempt rejected an overwrite
     */
    int upsertDispatchEvidence(@Param("items") List<ConversationToolDispatch> items);

    /**
     * Marks stale in-flight attempts UNKNOWN after process startup.
     *
     * @param recoveryTime UTC instant at which recovery classified the attempts
     * @param reason bounded operator-facing explanation without credentials
     * @return affected row count
     */
    int recoverStaleDispatches(@Param("recoveryTime") Instant recoveryTime,
                               @Param("reason") String reason);
}
