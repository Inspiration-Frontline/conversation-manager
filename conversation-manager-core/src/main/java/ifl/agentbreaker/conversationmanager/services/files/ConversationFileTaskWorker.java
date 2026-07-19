package ifl.agentbreaker.conversationmanager.services.files;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.OSSObject;
import ifl.agentbreaker.conversationmanager.config.ConversationFileProperties;
import ifl.agentbreaker.conversationmanager.dao.FileCleanupTaskMapper;
import ifl.agentbreaker.conversationmanager.dao.FileProcessingTaskMapper;
import ifl.agentbreaker.conversationmanager.dao.FileResourceMapper;
import ifl.agentbreaker.conversationmanager.domain.constants.ConversationFileStatus;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.FileCleanupTask;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.FileProcessingTask;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.FileResource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class ConversationFileTaskWorker
{
    @Autowired
    private FileResourceMapper fileResourceMapper;

    @Autowired
    private FileProcessingTaskMapper fileProcessingTaskMapper;

    @Autowired
    private FileCleanupTaskMapper fileCleanupTaskMapper;

    @Autowired
    private ConversationFileParser parser;

    @Autowired
    private FileContentSecurityScanner securityScanner;

    @Autowired
    private ConversationFileTaskService taskService;

    @Autowired
    private ConversationFileProperties properties;

    @Autowired
    private OSS ossClient;

    @Autowired
    @Qualifier("conversationFileTaskExecutor")
    private ExecutorService conversationFileTaskExecutor;

    @Autowired
    @Qualifier("conversationFileLeaseExecutor")
    private ScheduledExecutorService conversationFileLeaseExecutor;

    private volatile Semaphore concurrency;

    @Scheduled(fixedDelayString = "${agent-breaker.files.task-poll-milliseconds:500}")
    public void dispatchTasks()
    {
        Semaphore taskConcurrency = getConcurrency();
        int available = taskConcurrency.availablePermits();
        if (available <= 0)
            return;

        String processingLeaseToken = UUID.randomUUID().toString();
        List<FileProcessingTask> processingTasks = fileProcessingTaskMapper.claimTasks(
            processingLeaseToken, properties.getTaskLeaseSeconds(), available);
        for (FileProcessingTask task : processingTasks)
            submit(taskConcurrency, () -> processFile(task));

        available = taskConcurrency.availablePermits();
        if (available <= 0)
            return;

        String cleanupLeaseToken = UUID.randomUUID().toString();
        List<FileCleanupTask> cleanupTasks = fileCleanupTaskMapper.claimTasks(
            cleanupLeaseToken, properties.getTaskLeaseSeconds(), available);
        for (FileCleanupTask task : cleanupTasks)
            submit(taskConcurrency, () -> cleanupFile(task));
    }

    private void processFile(FileProcessingTask task)
    {
        // A renewable lease makes processing restart-safe: another instance can reclaim the task if
        // this worker dies, while a healthy worker prevents duplicate parsing during long documents.
        ScheduledFuture<?> leaseRenewal = renewProcessingLease(task);
        try
        {
            // Recheck the durable resource state after claiming the task. A retry, deletion, or a
            // competing worker may have changed it between polling and execution.
            FileResource fileResource = fileResourceMapper.getFileResourceById(task.getFileResourceId());
            if (fileResource == null)
            {
                fileProcessingTaskMapper.markFailed(task.getId(), task.getLeaseToken(), "File resource does not exist.");
                return;
            }
            if (fileResource.getStatus() == ConversationFileStatus.VALIDATING
                && fileResourceMapper.markProcessing(fileResource.getId(), fileResource.getCreatorId()) != 1)
            {
                fileProcessingTaskMapper.markCompleted(task.getId(), task.getLeaseToken());
                return;
            }
            if (fileResource.getStatus() != ConversationFileStatus.VALIDATING
                && fileResource.getStatus() != ConversationFileStatus.PROCESSING)
            {
                fileProcessingTaskMapper.markCompleted(task.getId(), task.getLeaseToken());
                return;
            }
            fileResource.setStatus(ConversationFileStatus.PROCESSING);

            try
            {
                // Processing is more than text extraction: it bounds the OSS read, performs the
                // security gate, verifies MIME/checksum, extracts text plus structural metadata,
                // and atomically marks both the resource and task complete.
                byte[] bytes = readObject(fileResource);
                securityScanner.scan(bytes);
                FileExtractionResult extractionResult = parser.parse(fileResource, bytes);
                taskService.completeProcessing(task.getId(), task.getLeaseToken(), fileResource, extractionResult);
            }
            catch (FileProcessingException e)
            {
                log.warn("File processing failed for {} with {}.", fileResource.getFileId(), e.getErrorCode(), e);
                taskService.failProcessing(
                    task.getId(), task.getLeaseToken(), fileResource, e.getErrorCode(), e.getMessage());
            }
            catch (Exception e)
            {
                log.error("Unexpected file processing failure for {}.", fileResource.getFileId(), e);
                taskService.failProcessing(
                    task.getId(), task.getLeaseToken(), fileResource, "FILE_PROCESSING_FAILED", "The file could not be processed.");
            }
        }
        finally
        {
            leaseRenewal.cancel(false);
        }
    }

    private void cleanupFile(FileCleanupTask task)
    {
        ScheduledFuture<?> leaseRenewal = renewCleanupLease(task);
        try
        {
            FileResource fileResource = fileResourceMapper.getFileResourceById(task.getFileResourceId());
            if (fileResource == null || fileResource.isDeleted())
            {
                fileCleanupTaskMapper.markCompleted(task.getId(), task.getLeaseToken());
                return;
            }

            Date now = new Date();
            boolean activelyReserved = fileResource.getReservedUntil() != null && fileResource.getReservedUntil().after(now);
            if (activelyReserved || fileResourceMapper.hasRoundReferences(fileResource.getId()))
            {
                fileCleanupTaskMapper.reschedule(
                    task.getId(),
                    task.getLeaseToken(),
                    properties.getOrphanTtlSeconds(),
                    "Cleanup deferred because the file is referenced.");
                return;
            }

            try
            {
                if (ossClient.doesObjectExist(fileResource.getBucketName(), fileResource.getObjectKey()))
                    ossClient.deleteObject(fileResource.getBucketName(), fileResource.getObjectKey());
                taskService.completeCleanup(task.getId(), task.getLeaseToken(), fileResource);
            }
            catch (Exception e)
            {
                log.warn("File cleanup failed for {}.", fileResource.getFileId(), e);
                long retryDelay = Math.min(300, Math.max(5, 1L << Math.min(task.getAttempt(), 8)));
                fileCleanupTaskMapper.reschedule(
                    task.getId(), task.getLeaseToken(), retryDelay, "OSS cleanup failed; retry scheduled.");
            }
        }
        finally
        {
            leaseRenewal.cancel(false);
        }
    }

    private ScheduledFuture<?> renewProcessingLease(FileProcessingTask task)
    {
        long intervalSeconds = Math.max(1, properties.getTaskLeaseSeconds() / 3L);
        return conversationFileLeaseExecutor.scheduleAtFixedRate(() ->
        {
            try
            {
                if (fileProcessingTaskMapper.renewLease(
                    task.getId(), task.getLeaseToken(), properties.getTaskLeaseSeconds()) != 1)
                    log.warn("The processing lease for task {} could not be renewed.", task.getId());
            }
            catch (Exception e)
            {
                log.warn("The processing lease for task {} could not be renewed.", task.getId(), e);
            }
        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
    }

    private ScheduledFuture<?> renewCleanupLease(FileCleanupTask task)
    {
        long intervalSeconds = Math.max(1, properties.getTaskLeaseSeconds() / 3L);
        return conversationFileLeaseExecutor.scheduleAtFixedRate(() ->
        {
            try
            {
                if (fileCleanupTaskMapper.renewLease(
                    task.getId(), task.getLeaseToken(), properties.getTaskLeaseSeconds()) != 1)
                    log.warn("The cleanup lease for task {} could not be renewed.", task.getId());
            }
            catch (Exception e)
            {
                log.warn("The cleanup lease for task {} could not be renewed.", task.getId(), e);
            }
        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
    }

    private byte[] readObject(FileResource fileResource) throws FileProcessingException
    {
        int maximumBytes = Math.toIntExact(properties.getMaxBytes());
        try (OSSObject object = ossClient.getObject(fileResource.getBucketName(), fileResource.getObjectKey());
             InputStream input = object.getObjectContent())
        {
            byte[] bytes = input.readNBytes(maximumBytes + 1);
            if (bytes.length > maximumBytes)
                throw new FileProcessingException("FILE_TOO_LARGE", "The uploaded file exceeds the configured size limit.");
            return bytes;
        }
        catch (FileProcessingException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new FileProcessingException("OSS_READ_FAILED", "The uploaded file could not be read.", e);
        }
    }

    private void submit(Semaphore taskConcurrency, Runnable task)
    {
        if (!taskConcurrency.tryAcquire())
            return;
        conversationFileTaskExecutor.submit(() ->
        {
            try
            {
                task.run();
            }
            finally
            {
                taskConcurrency.release();
            }
        });
    }

    private Semaphore getConcurrency()
    {
        Semaphore current = concurrency;
        if (current == null)
        {
            synchronized (this)
            {
                current = concurrency;
                if (current == null)
                {
                    current = new Semaphore(Math.max(1, properties.getTaskConcurrency()));
                    concurrency = current;
                }
            }
        }
        return current;
    }
}
