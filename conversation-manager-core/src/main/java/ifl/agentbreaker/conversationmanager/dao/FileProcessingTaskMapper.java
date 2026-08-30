package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.FileProcessingTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** MyBatis operations for the file processing task table. */
@Mapper
public interface FileProcessingTaskMapper
{
    /** Enqueues a pending extraction task unless one is already active.
     * @param fileResourceId file resource to process
     * @param userId owner of the resource
     * @return number of affected rows
     */
    int upsertPendingTask(@Param("fileResourceId") long fileResourceId, @Param("userId") long userId);

    /** Claims a bounded batch of extraction tasks for a worker lease.
     * @param leaseToken unique token used for subsequent state transitions
     * @param leaseSeconds lease duration from the claim time
     * @param limit maximum number of tasks to claim
     * @return claimed tasks, possibly empty
     */
    List<FileProcessingTask> claimTasks(@Param("leaseToken") String leaseToken,
                                        @Param("leaseSeconds") int leaseSeconds,
                                        @Param("limit") int limit);

    /** Marks a leased extraction task complete.
     * @param id task identity
     * @param leaseToken lease currently held by the worker
     * @return number of updated rows
     */
    int markCompleted(@Param("id") long id, @Param("leaseToken") String leaseToken);

    /** Records an extraction failure and releases the worker lease.
     * @param id task identity
     * @param leaseToken lease currently held by the worker
     * @param lastError bounded diagnostic retained for operations
     * @return number of updated rows
     */
    int markFailed(@Param("id") long id,
                   @Param("leaseToken") String leaseToken,
                   @Param("lastError") String lastError);

    /** Extends a lease while extraction is still running.
     * @param id task identity
     * @param leaseToken lease currently held by the worker
     * @param leaseSeconds additional lease duration
     * @return number of updated rows
     */
    int renewLease(@Param("id") long id,
                   @Param("leaseToken") String leaseToken,
                   @Param("leaseSeconds") int leaseSeconds);

    /** Cancels processing when the resource is deleted or otherwise no longer eligible.
     * @param fileResourceId file resource to cancel
     * @return number of updated rows
     */
    int cancelByFileResourceId(@Param("fileResourceId") long fileResourceId);
}
