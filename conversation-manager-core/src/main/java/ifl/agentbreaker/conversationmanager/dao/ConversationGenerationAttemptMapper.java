package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationGenerationAttempt;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.GenerationAttemptTerminalUpdate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** MyBatis operations for idempotent provider generation attempts. */
@Mapper
public interface ConversationGenerationAttemptMapper
{
    /** Inserts an attempt unless the same idempotency identity already exists.
     * @param attempt validated attempt metadata
     * @return inserted or existing attempt row
     */
    ConversationGenerationAttempt insertOrGet(ConversationGenerationAttempt attempt);

    /** Loads an attempt by its stable idempotency identity.
     * @param attemptId Runner-supplied attempt identity
     * @return existing attempt, or null
     */
    ConversationGenerationAttempt getByAttemptId(@Param("attemptId") String attemptId);

    /** Marks an attempt as materialized and optionally records its terminal metadata.
     * @param update terminal status, audit values, and selecting attempt identity
     * @return updated row count
     */
    int updateTerminal(GenerationAttemptTerminalUpdate update);
}
