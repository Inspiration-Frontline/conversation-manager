package ifl.agentbreaker.conversationmanager.services.files;

import ifl.agentbreaker.conversationmanager.dao.FileCleanupTaskMapper;
import ifl.agentbreaker.conversationmanager.dao.FileProcessingTaskMapper;
import ifl.agentbreaker.conversationmanager.dao.FileResourceMapper;
import ifl.agentbreaker.conversationmanager.dao.FileResourceVariantMapper;
import ifl.agentbreaker.conversationmanager.domain.constants.FileVariantType;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.FileResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Commits file-resource and task terminal states together in one transaction. */
@Service
public class ConversationFileTaskService
{
    /** Mapper updating the file resource's durable processing state. */
    @Autowired
    private FileResourceMapper fileResourceMapper;

    /** Mapper completing the currently leased extraction task. */
    @Autowired
    private FileProcessingTaskMapper fileProcessingTaskMapper;

    /** Mapper completing the currently leased object-cleanup task. */
    @Autowired
    private FileCleanupTaskMapper fileCleanupTaskMapper;

    /** Mapper publishing and deleting deterministic image variants. */
    @Autowired
    private FileResourceVariantMapper fileResourceVariantMapper;

    /** Creates or resets the deterministic derivative row before OSS publication.
     * @param fileResource original resource
     * @param objectKey deterministic derivative key
     */
    @Transactional(rollbackFor = Exception.class)
    public void prepareImageVariant(FileResource fileResource, String objectKey)
    {
        if (fileResourceVariantMapper.upsertPending(
            fileResource.getId(), fileResource.getCreatorId(), FileVariantType.MODEL_INPUT,
            fileResource.getBucketName(), objectKey) != 1)
            throw new IllegalStateException("The image variant could not be prepared.");
    }

    /** Marks a leased processing task READY and stores extracted text and typed metadata.
     * @param taskId database identity of the leased processing task
     * @param leaseToken ownership token preventing stale workers from committing
     * @param fileResource owned file being transitioned to READY
     * @param extractionResult validated parser output to persist
     * @param sanitizedImage verified image derivative, or {@code null} for non-image files
     */
    @Transactional(rollbackFor = Exception.class)
    public void completeProcessing(long taskId,
                                   String leaseToken,
                                   FileResource fileResource,
                                   FileExtractionResult extractionResult,
                                   SanitizedImage sanitizedImage)
    {
        if (sanitizedImage != null && fileResourceVariantMapper.markReady(
            fileResource.getId(), fileResource.getCreatorId(), FileVariantType.MODEL_INPUT,
            sanitizedImage.mimeType(), sanitizedImage.bytes().length, sanitizedImage.sha256(),
            sanitizedImage.width(), sanitizedImage.height()) != 1)
            throw new IllegalStateException("The image variant was not in PENDING state.");

        int updated = fileResourceMapper.markReady(
            fileResource.getId(),
            fileResource.getCreatorId(),
            extractionResult.detectedMimeType(),
            extractionResult.sha256(),
            extractionResult.extractedText(),
            extractionResult.metadata(),
            extractionResult.truncated(),
            sanitizedImage == null ? null : sanitizedImage.sourceWidth(),
            sanitizedImage == null ? null : sanitizedImage.sourceHeight());

        if (updated != 1)
            throw new IllegalStateException("The file resource was not in PROCESSING state.");

        if (fileProcessingTaskMapper.markCompleted(taskId, leaseToken) != 1)
            throw new IllegalStateException("The file processing task lease was lost.");
    }

    /** Marks a leased processing task FAILED and records a retryable error on the file resource.
     * @param taskId database identity of the leased processing task
     * @param leaseToken ownership token preventing stale workers from committing
     * @param fileResource owned file being transitioned to FAILED
     * @param errorCode stable file-processing failure code
     * @param errorMessage diagnostic failure explanation
     */
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

    /** Marks a physical cleanup task complete and finalizes the logical file deletion.
     * @param taskId database identity of the leased cleanup task
     * @param leaseToken ownership token preventing stale workers from committing
     * @param fileResource owned file whose object was removed
     */
    @Transactional(rollbackFor = Exception.class)
    public void completeCleanup(long taskId, String leaseToken, FileResource fileResource)
    {
        fileResourceVariantMapper.deleteByFileResourceId(fileResource.getId());
        fileResourceMapper.markDeleted(fileResource.getId(), fileResource.getCreatorId());
        if (fileCleanupTaskMapper.markCompleted(taskId, leaseToken) != 1)
            throw new IllegalStateException("The file cleanup task lease was lost.");
    }
}
