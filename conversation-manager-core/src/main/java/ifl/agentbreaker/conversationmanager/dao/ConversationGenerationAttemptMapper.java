package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationGenerationAttempt;
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
     * @param attemptId stable attempt identity
     * @param userId authenticated owner
     * @param status terminal status
     * @param providerRequestId bounded provider request identifier
     * @param errorCode safe failure classification
     * @param errorMessage safe failure message
     * @param endTime terminal time
     * @return updated row count
     */
    int updateTerminal(@Param("attemptId") String attemptId,
                       @Param("userId") long userId,
                       @Param("status") String status,
                       @Param("providerRequestId") String providerRequestId,
                       @Param("errorCode") String errorCode,
                       @Param("errorMessage") String errorMessage,
                       @Param("endTime") java.time.Instant endTime);
}
