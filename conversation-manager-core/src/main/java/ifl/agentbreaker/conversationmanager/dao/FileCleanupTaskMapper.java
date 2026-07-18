package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.constants.FileCleanupReason;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.FileCleanupTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FileCleanupTaskMapper
{
    int scheduleTask(@Param("fileResourceId") long fileResourceId,
                     @Param("userId") long userId,
                     @Param("reason") FileCleanupReason reason,
                     @Param("delaySeconds") long delaySeconds);

    List<FileCleanupTask> claimTasks(@Param("leaseToken") String leaseToken,
                                     @Param("leaseSeconds") int leaseSeconds,
                                     @Param("limit") int limit);

    int markCompleted(@Param("id") long id, @Param("leaseToken") String leaseToken);

    int reschedule(@Param("id") long id,
                   @Param("leaseToken") String leaseToken,
                   @Param("delaySeconds") long delaySeconds,
                   @Param("lastError") String lastError);

    int renewLease(@Param("id") long id,
                   @Param("leaseToken") String leaseToken,
                   @Param("leaseSeconds") int leaseSeconds);

    int cancelByFileResourceId(@Param("fileResourceId") long fileResourceId);
}
