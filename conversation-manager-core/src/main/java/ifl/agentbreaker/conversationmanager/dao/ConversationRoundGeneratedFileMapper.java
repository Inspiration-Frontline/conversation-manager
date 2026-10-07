package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationRoundGeneratedFile;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.GeneratedFileHistory;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.MaterializedGeneratedFile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/** MyBatis operations scoped to generic Round generated-output relations. */
@Mapper
public interface ConversationRoundGeneratedFileMapper
{
    /** Inserts one generated-output relation idempotently.
     * @param relation validated relation entity
     * @return affected row count
     */
    int insertGeneratedFile(ConversationRoundGeneratedFile relation);

    /** Loads the generated relation for one idempotent attempt.
     * @param generationAttemptId internal attempt identity
     * @return relation, or null when materialization has not committed
     */
    ConversationRoundGeneratedFile getByGenerationAttemptId(@Param("generationAttemptId") long generationAttemptId);

    /** Loads the committed file identity for one materialized generation attempt.
     * @param generationAttemptId internal attempt identity
     * @return materialized file projection, or null when the attempt has no committed relation
     */
    MaterializedGeneratedFile getMaterializedFileByAttemptId(@Param("generationAttemptId") long generationAttemptId);

    /** Lists generated outputs for owner history projection.
     * @param conversationId stable Conversation identity
     * @return generated outputs ordered by Round and output order
     */
    List<GeneratedFileHistory> listRoundGeneratedFiles(@Param("conversationId") String conversationId);

    /** Lists generated outputs inside an immutable completed snapshot.
     * @param conversationId stable Conversation identity
     * @param endRoundNumber inclusive snapshot boundary
     * @return generated outputs inside the frozen boundary
     */
    List<GeneratedFileHistory> listCompletedGeneratedFilesAtOrBefore(
        @Param("conversationId") String conversationId,
        @Param("endRoundNumber") long endRoundNumber);

    /** Resolves generated outputs only inside completed Rounds of a shared snapshot.
     * @param conversationId stable source Conversation identifier
     * @param endRoundNumber inclusive frozen share boundary
     * @param fileIds requested stable generated-file identifiers
     * @return authorized generated outputs in request order
     */
    List<GeneratedFileHistory> listSharedGeneratedFiles(
        @Param("conversationId") String conversationId,
        @Param("endRoundNumber") long endRoundNumber,
        @Param("fileIds") Collection<String> fileIds);

    /** Resolves the latest visible Round that produced one generated resource.
     * @param conversationId stable Conversation identity
     * @param fileResourceId internal file resource identity
     * @return latest visible Round number, or null when the resource is not a visible output
     */
    Long findLatestVisibleRoundNumber(@Param("conversationId") String conversationId,
                                      @Param("fileResourceId") long fileResourceId);

    /** Marks the selected outputs superseded for Regenerate presentation.
     * @param roundId containing Round database identity
     * @param fileResourceIds selected generated resources
     * @param userId authenticated owner recorded in audit fields
     * @return affected relation count
     */
    int markSuperseded(@Param("roundId") long roundId,
                       @Param("fileResourceIds") Collection<Long> fileResourceIds,
                       @Param("userId") long userId);

    /** Marks every active generated output in one completed Round as superseded.
     * @param roundId containing Round database identity
     * @param userId authenticated owner recorded in audit fields
     * @return affected relation count
     */
    int markAllSuperseded(@Param("roundId") long roundId, @Param("userId") long userId);
}
