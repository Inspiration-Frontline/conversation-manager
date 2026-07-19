package ifl.agentbreaker.conversationmanager.services.files;

import ifl.agentbreaker.conversationmanager.dao.FileCleanupTaskMapper;
import ifl.agentbreaker.conversationmanager.dao.FileProcessingTaskMapper;
import ifl.agentbreaker.conversationmanager.dao.FileResourceMapper;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.FileResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationFileTaskService
{
    @Autowired
    private FileResourceMapper fileResourceMapper;

    @Autowired
    private FileProcessingTaskMapper fileProcessingTaskMapper;

    @Autowired
    private FileCleanupTaskMapper fileCleanupTaskMapper;

    @Transactional(rollbackFor = Exception.class)
    public void completeProcessing(long taskId,
                                   String leaseToken,
                                   FileResource fileResource,
                                   FileExtractionResult extractionResult)
    {
        int updated = fileResourceMapper.markReady(
            fileResource.getId(),
            fileResource.getCreatorId(),
            extractionResult.detectedMimeType(),
            extractionResult.sha256(),
            extractionResult.extractedText(),
            extractionResult.metadata(),
            extractionResult.truncated(),
            extractionResult.width(),
            extractionResult.height());
        if (updated != 1)
            throw new IllegalStateException("The file resource was not in PROCESSING state.");
        if (fileProcessingTaskMapper.markCompleted(taskId, leaseToken) != 1)
            throw new IllegalStateException("The file processing task lease was lost.");
    }

    @Transactional(rollbackFor = Exception.class)
    public void failProcessing(long taskId,
                               String leaseToken,
                               FileResource fileResource,
                               String errorCode,
                               String errorMessage)
    {
        fileResourceMapper.markFailed(fileResource.getId(), fileResource.getCreatorId(), errorCode, errorMessage);
        fileProcessingTaskMapper.markFailed(taskId, leaseToken, errorMessage);
    }

    @Transactional(rollbackFor = Exception.class)
    public void completeCleanup(long taskId, String leaseToken, FileResource fileResource)
    {
        fileResourceMapper.markDeleted(fileResource.getId(), fileResource.getCreatorId());
        if (fileCleanupTaskMapper.markCompleted(taskId, leaseToken) != 1)
            throw new IllegalStateException("The file cleanup task lease was lost.");
    }
}
