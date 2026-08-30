package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.dtos.ConversationReferenceBoundary;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationRound;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.time.Instant;
import java.util.List;

/** MyBatis persistence operations for Round creation, progress, history, and fork copying. */
@Mapper
public interface ConversationRoundMapper
{
    /**
     * Inserts one terminal Round with its assigned monotonic number.
     *
     * @param round initialized terminal Round
     * @return inserted Round carrying generated database identity and timestamps
     */
    ConversationRound insertRound(ConversationRound round);

    /**
     * Creates an in-progress Round checkpoint at revision zero.
     *
     * @param round initialized checkpoint Round
     * @return inserted checkpoint carrying generated database identity and timestamps
     */
    ConversationRound insertCheckpoint(ConversationRound round);

    /**
     * Advances an in-progress Round revision without changing its terminal fields. Append-progress
     * mutations use this after their append-only Turn or dispatch rows have been inserted.
     *
     * @param roundId database ID of the mutable Round
     * @param expectedRevision caller's optimistic-lock revision
     * @param modifierId authenticated user recorded as the modifier
     * @return {@code 1} when the expected revision matched, otherwise {@code 0}
     */
    int advanceRevision(@Param("roundId") long roundId, @Param("expectedRevision") long expectedRevision,
                        @Param("modifierId") long modifierId);

    /**
     * Advances a mutable Round revision and writes its terminal status, answer, error, and end
     * time atomically. Finalization uses this instead of {@link #advanceRevision(long, long, long)}
     * because it must prevent another append from racing with the terminal state transition.
     *
     * @param roundId database ID of the mutable Round
     * @param expectedRevision caller's optimistic-lock revision
     * @param modifierId authenticated user recorded as the modifier
     * @param status terminal Round status
     * @param finalAnswerContent nullable final text answer
     * @param finalAnswerContentParts nullable structured final answer parts
     * @param finalSourceTurnNumber nullable source Turn that produced the answer
     * @param errorMessage nullable terminal failure or cancellation reason
     * @param endTime terminal completion time
     * @return {@code 1} when finalization committed, otherwise {@code 0}
     */
    int finalizeRound(@Param("roundId") long roundId, @Param("expectedRevision") long expectedRevision,
                      @Param("modifierId") long modifierId, @Param("status") String status,
                      @Param("finalAnswerContent") String finalAnswerContent,
                      @Param("finalAnswerContentParts") String finalAnswerContentParts,
                      @Param("finalSourceTurnNumber") Long finalSourceTurnNumber,
                      @Param("errorMessage") String errorMessage, @Param("endTime") Instant endTime);

    /**
     * Loads one active Round by its stable Conversation boundary and monotonic number.
     *
     * @param conversationId stable Conversation identifier
     * @param roundNumber Round number within that Conversation
     * @return matching active Round, or {@code null} when absent
     */
    ConversationRound getRound(@Param("conversationId") String conversationId,
                               @Param("roundNumber") long roundNumber);

    /**
     * Lists all active Rounds used for owner history rendering.
     *
     * @param conversationId stable Conversation identifier
     * @return Rounds ordered by monotonic Round number
     */
    List<ConversationRound> listActiveRounds(@Param("conversationId") String conversationId);

    /**
     * Lists completed model-context Rounds up to an inclusive snapshot boundary.
     *
     * @param conversationId stable Conversation identifier
     * @param endRoundNumber inclusive upper Round boundary
     * @return completed, non-deleted Rounds in ascending order
     */
    List<ConversationRound> listCompletedRoundsAtOrBefore(@Param("conversationId") String conversationId,
                                                           @Param("endRoundNumber") long endRoundNumber);

    /**
     * Resolves requested source boundaries in one set-based query.
     *
     * @param boundaries stable Conversation IDs paired with inclusive Round numbers
     * @return matching active boundary Rounds
     */
    List<ConversationRound> listRoundsAtBoundaries(
        @Param("boundaries") Collection<ConversationReferenceBoundary> boundaries);

    /**
     * Loads all completed evidence Rounds for multiple frozen Conversation-reference boundaries.
     *
     * @param boundaries stable Conversation IDs paired with inclusive Round numbers
     * @return completed Rounds grouped by source Conversation and ordered by Round number
     */
    List<ConversationRound> listCompletedRoundsAtOrBeforeBoundaries(
        @Param("boundaries") Collection<ConversationReferenceBoundary> boundaries);

    /**
     * Filters candidate Conversations down to those with at least one completed active Round.
     *
     * @param conversationIds stable candidate Conversation identifiers
     * @return identifiers that contain referenceable completed history
     */
    List<String> listConversationIdsWithCompletedRounds(
        @Param("conversationIds") Collection<String> conversationIds);

    /**
     * Finds the latest completed Round at or before an inclusive snapshot boundary.
     *
     * @param conversationId stable Conversation identifier
     * @param endRoundNumber inclusive upper Round boundary
     * @return latest matching Round, or {@code null} when none is completed
     */
    ConversationRound getLatestCompletedRoundAtOrBefore(@Param("conversationId") String conversationId,
                                                         @Param("endRoundNumber") long endRoundNumber);

    /**
     * Invokes the set-based PostgreSQL fork function to copy replayable normalized history.
     *
     * @param sourceConversationId stable source Conversation identifier
     * @param targetConversationId stable target Conversation identifier
     * @param userId authenticated owner of the fork
     * @param endRoundNumber inclusive completed snapshot boundary
     * @return number of copied Rounds
     */
    int forkConversationHistory(@Param("sourceConversationId") String sourceConversationId,
                                @Param("targetConversationId") String targetConversationId,
                                @Param("userId") long userId,
                                @Param("endRoundNumber") long endRoundNumber);

    /**
     * Restores original per-Round trace correlation after set-based fork ID remapping.
     *
     * @param sourceConversationId stable source Conversation identifier
     * @param targetConversationId stable target Conversation identifier
     * @param endRoundNumber inclusive completed snapshot boundary
     * @return number of target Round rows updated
     */
    int copyForkedRoundTraceIds(@Param("sourceConversationId") String sourceConversationId,
                                @Param("targetConversationId") String targetConversationId,
                                @Param("endRoundNumber") long endRoundNumber);

}
