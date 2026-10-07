package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.dtos.responses.EditSourceHistory;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationRoundEditSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/** MyBatis operations scoped to image-editing source provenance. */
@Mapper
public interface ConversationRoundEditSourceMapper
{
    /** Inserts one idempotent edit-source provenance row.
     * @param editSource validated provenance entity
     * @return affected row count
     */
    int insertEditSource(ConversationRoundEditSource editSource);

    /** Loads provenance for one editing Round.
     * @param roundId database identity of the editing Round
     * @return provenance entity, or null when the Round is not an edit
     */
    ConversationRoundEditSource getByRoundId(@Param("roundId") long roundId);

    /** Lists provenance projections for owner and shared Round history.
     * @param roundIds database identities of the visible Rounds
     * @return bounded provenance summaries
     */
    List<EditSourceHistory> listByRoundIds(@Param("roundIds") Collection<Long> roundIds);

    /** Deletes provenance rows before their owning Conversations are logically deleted.
     * @param conversationIds stable Conversation identifiers being deleted
     * @param userId authenticated owner recorded by the cleanup operation
     * @return affected row count
     */
    int deleteByConversationIds(@Param("conversationIds") Collection<String> conversationIds,
                                @Param("userId") long userId);
}
