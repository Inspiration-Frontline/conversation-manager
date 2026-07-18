package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.FileProcessingTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FileProcessingTaskMapper
{
    int upsertPendingTask(@Param("fileResourceId") long fileResourceId, @Param("userId") long userId);

    List<FileProcessingTask> claimTasks(@Param("leaseToken") String leaseToken,
                                        @Param("leaseSeconds") int leaseSeconds,
                                        @Param("limit") int limit);

    int markCompleted(@Param("id") long id, @Param("leaseToken") String leaseToken);

    int markFailed(@Param("id") long id,
                   @Param("leaseToken") String leaseToken,
                   @Param("lastError") String lastError);

    int renewLease(@Param("id") long id,
                   @Param("leaseToken") String leaseToken,
                   @Param("leaseSeconds") int leaseSeconds);

    int cancelByFileResourceId(@Param("fileResourceId") long fileResourceId);
}
