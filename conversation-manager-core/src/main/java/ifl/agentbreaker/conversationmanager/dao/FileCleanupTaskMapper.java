package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.constants.FileCleanupReason;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.FileCleanupTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/** MyBatis operations for the file cleanup task table. */
@Mapper
public interface FileCleanupTaskMapper
{
    /** Enqueues or refreshes a pending cleanup task for one file resource.
     * @param fileResourceId stable file resource identity
     * @param userId owner whose storage namespace contains the file
     * @param reason reason retained for audit and retry handling
     * @param delaySeconds minimum delay before the task becomes claimable
     * @return number of affected rows
     */
    int addTask(@Param("fileResourceId") long fileResourceId,
                @Param("userId") long userId,
                @Param("reason") FileCleanupReason reason,
                @Param("delaySeconds") long delaySeconds);

    /** Enqueues cleanup tasks for file resources referenced by the supplied Conversations.
     * @param conversationIds owned Conversation identifiers
     * @param userId owner of all supplied Conversations
     * @param reason reason retained for audit and retry handling
     * @param delaySeconds minimum delay before tasks become claimable
     * @return number of affected rows
     */
    int addTasksForConversationReferences(
        @Param("conversationIds") Collection<String> conversationIds,
        @Param("userId") long userId,
        @Param("reason") FileCleanupReason reason,
        @Param("delaySeconds") long delaySeconds);

    /** Claims a bounded batch and assigns the caller's lease token.
     * @param leaseToken unique token used for subsequent state transitions
     * @param leaseSeconds lease duration from the claim time
     * @param limit maximum number of tasks to claim
     * @return claimed tasks, possibly empty
     */
    List<FileCleanupTask> claimTasks(@Param("leaseToken") String leaseToken,
                                     @Param("leaseSeconds") int leaseSeconds,
                                     @Param("limit") int limit);

    /** Marks a leased task complete when its physical deletion succeeded.
     * @param id task identity
     * @param leaseToken lease currently held by the worker
     * @return number of updated rows
     */
    int markCompleted(@Param("id") long id, @Param("leaseToken") String leaseToken);

    /** Returns a failed task to the pending queue after a retry delay.
     * @param id task identity
     * @param leaseToken lease currently held by the worker
     * @param delaySeconds delay before the next claim
     * @param lastError bounded diagnostic retained for operations
     * @return number of updated rows
     */
    int reschedule(@Param("id") long id,
                   @Param("leaseToken") String leaseToken,
                   @Param("delaySeconds") long delaySeconds,
                   @Param("lastError") String lastError);

    /** Extends a lease while a worker is still deleting the object.
     * @param id task identity
     * @param leaseToken lease currently held by the worker
     * @param leaseSeconds additional lease duration
     * @return number of updated rows
     */
    int renewLease(@Param("id") long id,
                   @Param("leaseToken") String leaseToken,
                   @Param("leaseSeconds") int leaseSeconds);

    /** Cancels pending cleanup tasks for resources that became referenced again.
     * @param fileResourceIds file resource identities to cancel
     * @return number of updated rows
     */
    int cancelByFileResourceIds(@Param("fileResourceIds") Collection<Long> fileResourceIds);
}
