package ifl.agentbreaker.conversationmanager.services.files;

import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.ObjectMetadata;
import ifl.agentbreaker.authcenter.session.UserContextService;
import ifl.agentbreaker.conversationmanager.config.ConversationFileProperties;
import ifl.agentbreaker.conversationmanager.config.OssStorageProperties;
import ifl.agentbreaker.conversationmanager.dao.FileCleanupTaskMapper;
import ifl.agentbreaker.conversationmanager.dao.FileProcessingTaskMapper;
import ifl.agentbreaker.conversationmanager.dao.FileResourceMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationRoundFileMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationSharingMapper;
import ifl.agentbreaker.conversationmanager.domain.constants.ConversationFileKind;
import ifl.agentbreaker.conversationmanager.domain.constants.ConversationFileStatus;
import ifl.agentbreaker.conversationmanager.domain.constants.FileCleanupReason;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.ConfirmFileUploadRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.ConfirmFileUploadItem;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.CreateFileUploadSessionRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.DeleteFileResourceRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.RetryFileProcessingRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.FileDownloadUrl;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.FileResourceInfo;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.FileUploadSession;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.FileResource;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationSharing;
import ifl.agentbreaker.conversationmanager.exceptions.ServiceResponseException;
import ifl.agentbreaker.conversationmanager.support.BusinessIdManager;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import stark.dataworks.boot.autoconfig.web.LogArgumentsAndResponse;
import stark.dataworks.boot.web.ServiceResponse;

import java.net.URL;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Coordinates the user-file lifecycle across PostgreSQL and private OSS. The service owns stable
 * file identity, authorization, reservation, and cleanup scheduling; Runner receives only
 * extracted evidence or short-lived URLs and never receives storage credentials.
 */
@Service
@LogArgumentsAndResponse
public class ConversationFileService
{
    public static final int ERROR_INVALID_FILE = 2300;
    public static final int ERROR_FILE_NOT_FOUND = 2301;
    public static final int ERROR_FILE_BUSY = 2302;

    /**
     * OSS layout: configured prefix / owner / UTC year / UTC month / stable file ID / source.
     * Date partitioning keeps operational listings bounded; the stable file ID prevents filename
     * collisions and the original untrusted filename never becomes part of the object key.
     */
    private static final String OBJECT_KEY_LAYOUT = "%s/%d/%04d/%02d/%s/source";

    @Autowired
    private FileResourceMapper fileResourceMapper;

    @Autowired
    private FileProcessingTaskMapper fileProcessingTaskMapper;

    @Autowired
    private FileCleanupTaskMapper fileCleanupTaskMapper;

    @Autowired
    private ConversationRoundFileMapper conversationRoundFileMapper;

    @Autowired
    private ConversationSharingMapper conversationSharingMapper;

    @Autowired
    private ConversationFileProperties fileProperties;

    @Autowired
    private OssStorageProperties ossProperties;

    @Autowired
    private OSS ossClient;

    @Autowired
    private TransactionTemplate transactionTemplate;

    /**
     * Creates a user-owned resource before the browser uploads bytes directly to OSS. Persisting
     * the resource first gives the confirm step a server-owned expected size, MIME type, expiry,
     * and object key to verify against the untrusted client upload.
     *
     * @param request original filename, declared MIME type, and expected byte size
     * @return upload metadata plus a short-lived signed PUT URL
     */
    @Transactional(rollbackFor = Exception.class)
    public ServiceResponse<FileUploadSession> createFileUploadSession(CreateFileUploadSessionRequest request)
    {
        long userId = UserContextService.getCurrentUserId();
        String filename = ConversationFileTypeResolver.normalizeFilename(request.getOriginalFilename());
        String extension = ConversationFileTypeResolver.getExtension(filename);
        String mimeType = request.getMimeType().trim().toLowerCase(Locale.ROOT);
        validateUpload(filename, extension, mimeType, request.getFileSize());

        ConversationFileKind kind = ConversationFileTypeResolver.resolveKind(extension);
        String fileId = BusinessIdManager.newFileId();
        Date expiresAt = Date.from(Instant.now().plusSeconds(ossProperties.getPresignedUrlTtlSeconds()));
        String objectKey = buildObjectKey(userId, fileId);

        FileResource fileResource = new FileResource();
        fileResource.setCreatorId(userId);
        fileResource.setModifierId(userId);
        fileResource.setFileId(fileId);
        fileResource.setKind(kind);
        fileResource.setStatus(ConversationFileStatus.PENDING_UPLOAD);
        fileResource.setStatusRevision(1);
        fileResource.setBucketName(ossProperties.getBucketName());
        fileResource.setObjectKey(objectKey);
        fileResource.setOriginalFilename(filename);
        fileResource.setFileExtension(extension);
        fileResource.setDeclaredMimeType(mimeType);
        fileResource.setFileSize(request.getFileSize());
        fileResource.setUploadExpiresAt(expiresAt);
        FileResource inserted = fileResourceMapper.insertFileResource(fileResource);
        if (inserted == null)
            throw new IllegalStateException("The file resource could not be created.");
        fileCleanupTaskMapper.addTask(
            inserted.getId(), userId, FileCleanupReason.UPLOAD_EXPIRED,
            ossProperties.getPresignedUrlTtlSeconds());

        GeneratePresignedUrlRequest signedRequest = new GeneratePresignedUrlRequest(
            ossProperties.getBucketName(), objectKey, HttpMethod.PUT);
        signedRequest.setExpiration(expiresAt);
        signedRequest.setContentType(mimeType);
        URL signedUrl = ossClient.generatePresignedUrl(signedRequest);

        FileUploadSession session = new FileUploadSession();
        session.setFile(toFileResourceInfo(inserted));
        session.setMethod("PUT");
        session.setUploadUrl(signedUrl.toString());
        session.setExpiresAt(expiresAt);
        return ServiceResponse.buildSuccessResponse(session);
    }

    /**
     * Confirms every direct-to-OSS upload in one request, then schedules asynchronous inspection.
     * Confirmation is separate from session creation because the browser, not this service, sends
     * the bytes; the server must verify the resulting OSS object before parsing it.
     *
     * @param request file IDs and optional client-computed SHA-256 values
     * @return metadata for every confirmed resource
     */
    @Transactional(rollbackFor = Exception.class)
    public ServiceResponse<List<FileResourceInfo>> confirmFileUpload(ConfirmFileUploadRequest request)
    {
        long userId = UserContextService.getCurrentUserId();
        List<FileResourceInfo> results = new ArrayList<>();
        for (ConfirmFileUploadItem item : request.getFiles())
            results.add(confirmSingleUpload(item, userId));
        return ServiceResponse.buildSuccessResponse(results);
    }

    /**
     * Verifies one uploaded OSS object against the server-owned upload contract and transitions it
     * into asynchronous processing. Reconfirming an already confirmed resource is idempotent.
     *
     * @param request one file ID and checksum
     * @param userId authenticated owner
     * @return current public resource metadata
     */
    private FileResourceInfo confirmSingleUpload(ConfirmFileUploadItem request, long userId)
    {
        String fileId = request.getFileId();
        FileResource existing = requireOwnedFile(fileId, userId);
        if (existing.getStatus() != ConversationFileStatus.PENDING_UPLOAD)
            return toFileResourceInfo(existing);
        if (existing.getUploadExpiresAt().before(new Date()))
            throw new ServiceResponseException(ERROR_INVALID_FILE, "The upload session has expired.");

        ObjectMetadata metadata;
        try
        {
            metadata = ossClient.getObjectMetadata(existing.getBucketName(), existing.getObjectKey());
        }
        catch (Exception e)
        {
            throw new ServiceResponseException(ERROR_INVALID_FILE, "The uploaded OSS object could not be verified.");
        }
        if (metadata.getContentLength() != existing.getFileSize())
            throw new ServiceResponseException(ERROR_INVALID_FILE, "The uploaded file size does not match the request.");

        String sha256 = !StringUtils.hasText(request.getSha256())
            ? null
            : request.getSha256().toLowerCase(Locale.ROOT);
        String objectMimeType = StringUtils.hasText(metadata.getContentType())
            ? metadata.getContentType().toLowerCase(Locale.ROOT)
            : existing.getDeclaredMimeType();

        FileResource confirmed = transactionTemplate.execute(status -> {
            FileResource updated = fileResourceMapper.confirmUpload(
                fileId, userId, objectMimeType, metadata.getContentLength(), sha256);
            if (updated == null)
                throw new ServiceResponseException(ERROR_INVALID_FILE, "The upload cannot be confirmed in its current state.");
            fileProcessingTaskMapper.upsertPendingTask(updated.getId(), userId);
            fileCleanupTaskMapper.addTask(
                updated.getId(), userId, FileCleanupReason.ORPHANED, fileProperties.getOrphanTtlSeconds());
            return updated;
        });
        return toFileResourceInfo(confirmed);
    }

    /**
     * Returns metadata for one owned file; ownership is checked before any status or error detail
     * is exposed to avoid cross-user file probing.
     *
     * @param fileId stable file reference supplied by the browser
     * @return public metadata for the owned resource
     */
    public ServiceResponse<FileResourceInfo> getFileResource(String fileId)
    {
        long userId = UserContextService.getCurrentUserId();
        return ServiceResponse.buildSuccessResponse(toFileResourceInfo(requireOwnedFile(fileId, userId)));
    }

    /**
     * Requeues failed parser tasks as a batch. The operation is deliberately request-shaped around
     * a collection so a future bulk retry UI does not require a new endpoint contract.
     *
     * @param request owned file IDs currently in FAILED state
     * @return refreshed metadata for every requeued resource
     */
    @Transactional(rollbackFor = Exception.class)
    public ServiceResponse<List<FileResourceInfo>> retryFileProcessing(RetryFileProcessingRequest request)
    {
        long userId = UserContextService.getCurrentUserId();
        List<FileResourceInfo> results = new ArrayList<>();
        for (String fileId : request.getFileIds())
        {
            FileResource fileResource = requireOwnedFile(fileId, userId);
            if (fileResource.getStatus() != ConversationFileStatus.FAILED)
                throw new ServiceResponseException(ERROR_INVALID_FILE, "Only a failed file can be retried.");
            if (fileResourceMapper.resetFailedForRetry(fileResource.getId(), userId) != 1)
                throw new ServiceResponseException(ERROR_INVALID_FILE, "The file state changed before retry.");
            fileProcessingTaskMapper.upsertPendingTask(fileResource.getId(), userId);
            results.add(toFileResourceInfo(requireOwnedFile(fileId, userId)));
        }
        return ServiceResponse.buildSuccessResponse(results);
    }

    /**
     * Requests logical deletion and schedules physical OSS cleanup. The resource remains visible to
     * in-flight references until the cleanup worker can prove that no active Round/request uses it.
     *
     * @param request owned file IDs to delete
     * @return true after all logical transitions and cleanup tasks are recorded
     */
    @Transactional(rollbackFor = Exception.class)
    public ServiceResponse<Boolean> deleteFileResource(DeleteFileResourceRequest request)
    {
        long userId = UserContextService.getCurrentUserId();
        for (String fileId : request.getFileIds())
        {
            FileResource fileResource = requireOwnedFile(fileId, userId);
            if (fileResourceMapper.requestDelete(fileId, userId) != 1)
                throw new ServiceResponseException(ERROR_FILE_BUSY, "The file is referenced by an active request or conversation.");
            fileProcessingTaskMapper.cancelByFileResourceId(fileResource.getId());
            fileCleanupTaskMapper.addTask(fileResource.getId(), userId, FileCleanupReason.USER_REMOVED, 0);
        }
        return ServiceResponse.buildSuccessResponse(true);
    }

    /**
     * Mints a short-lived signed download URL for one READY file without exposing permanent OSS
     * credentials or allowing callers to download an unprocessed object.
     *
     * @param fileId stable file reference
     * @return signed URL and its expiry
     */
    public ServiceResponse<FileDownloadUrl> getFileDownloadUrl(String fileId)
    {
        long userId = UserContextService.getCurrentUserId();
        FileResource fileResource = requireOwnedFile(fileId, userId);
        if (fileResource.getStatus() != ConversationFileStatus.READY)
            throw new ServiceResponseException(ERROR_INVALID_FILE, "The file is not ready for download.");

        Date expiresAt = Date.from(Instant.now().plusSeconds(ossProperties.getPresignedUrlTtlSeconds()));
        FileDownloadUrl result = new FileDownloadUrl();
        result.setFileId(fileId);
        result.setUrl(createSignedGetUrl(fileResource, expiresAt));
        result.setExpiresAt(expiresAt);
        return ServiceResponse.buildSuccessResponse(result);
    }

    /**
     * Mints a signed URL only when the file is linked from a completed Round inside a valid share
     * boundary. The caller is authenticated by the HTTP filter; no owner identity is inferred from
     * the file request itself.
     */
    public ServiceResponse<FileDownloadUrl> getSharedFileDownloadUrl(
        String conversationId, long endRoundNumber, String fileId)
    {
        boolean visible = conversationRoundFileMapper.listRoundFiles(conversationId).stream()
            .anyMatch(file -> file.fileId().equals(fileId) && file.roundNumber() <= endRoundNumber);
        if (!visible)
            throw new ServiceResponseException(ERROR_FILE_NOT_FOUND, "File does not exist in the shared snapshot.");
        FileResource fileResource = conversationRoundFileMapper.listRoundFiles(conversationId).stream()
            .filter(file -> file.fileId().equals(fileId))
            .findFirst()
            .map(file -> fileResourceMapper.getFileResourceById(file.fileResourceId()))
            .orElse(null);
        if (fileResource == null || fileResource.getStatus() != ConversationFileStatus.READY || fileResource.isDeleted())
            throw new ServiceResponseException(ERROR_INVALID_FILE, "The file is not ready for download.");
        Date expiresAt = Date.from(Instant.now().plusSeconds(ossProperties.getPresignedUrlTtlSeconds()));
        FileDownloadUrl result = new FileDownloadUrl();
        result.setFileId(fileId);
        result.setUrl(createSignedGetUrl(fileResource, expiresAt));
        result.setExpiresAt(expiresAt);
        return ServiceResponse.buildSuccessResponse(result);
    }

    /** Resolves and authorizes a share before delegating to the boundary-checked file method. */
    public ServiceResponse<FileDownloadUrl> getSharedFileDownloadUrl(String sharedConversationId, String fileId)
    {
        ConversationSharing sharing = conversationSharingMapper.getActiveConversationSharingBySharedId(sharedConversationId);
        if (sharing == null)
            throw new ServiceResponseException(ERROR_FILE_NOT_FOUND, "Shared conversation does not exist or has expired.");
        return getSharedFileDownloadUrl(sharing.getParentConversationId(), sharing.getEndRoundNumber(), fileId);
    }

    /**
     * Batch-loads owned files for Runner's preparation RPC and restores caller order. A short
     * result signals that at least one stable ID is missing or unauthorized, allowing the caller to
     * reject the whole frozen selection instead of partially answering from an incomplete set.
     *
     * @param fileIds stable IDs selected for one message
     * @param userId authenticated owner used by the SQL authorization predicate
     * @return matching resources in the same order as {@code fileIds}
     */
    public List<FileResource> listOwnedFiles(Collection<String> fileIds, long userId)
    {
        if (fileIds == null || fileIds.isEmpty())
            return new ArrayList<>();
        List<FileResource> resources = fileResourceMapper.listOwnedFileResources(fileIds, userId);
        Map<String, FileResource> resourcesById = new LinkedHashMap<>();
        for (FileResource resource : resources)
            resourcesById.put(resource.getFileId(), resource);
        List<FileResource> ordered = new ArrayList<>();
        for (String fileId : fileIds)
        {
            FileResource resource = resourcesById.get(fileId);
            if (resource != null)
                ordered.add(resource);
        }
        return ordered;
    }

    /**
     * Atomically reserves a frozen file selection for one Runner request. Reservation closes the
     * race between file readiness polling and Round persistence: two concurrent sends cannot both
     * claim the same draft resource before the durable Round link exists.
     *
     * @param fileIds stable file IDs to reserve
     * @param userId authenticated owner
     * @param conversationId destination Conversation
     * @param requestId Runner correlation ID used for release/expiry
     * @return true only when every requested ID was reserved
     */
    public boolean reserveFilesForRequest(
        Collection<String> fileIds,
        long userId,
        String conversationId,
        String requestId)
    {
        return fileResourceMapper.reserveFileResources(
            fileIds,
            userId,
            conversationId,
            requestId,
            fileProperties.getReservationSeconds()) == fileIds.size();
    }

    /**
     * Releases one Conversation's file references through the same set-based implementation used
     * for bulk deletion, keeping the single-item API from reintroducing an N+1 write path.
     *
     * @param conversationId Conversation being deleted
     * @param userId authenticated owner
     */
    @Transactional(rollbackFor = Exception.class)
    public void releaseConversationReferences(String conversationId, long userId)
    {
        releaseConversationReferences(List.of(conversationId), userId);
    }

    /**
     * Releases file reservations and durable Round references for multiple Conversations as set
     * operations. Cleanup tasks are inserted directly from relation tables in one SQL statement;
     * no file ID is loaded into Java and no database call occurs inside a loop.
     *
     * @param conversationIds Conversations being deleted
     * @param userId authenticated owner
     */
    @Transactional(rollbackFor = Exception.class)
    public void releaseConversationReferences(Collection<String> conversationIds, long userId)
    {
        List<String> uniqueConversationIds = conversationIds == null
            ? List.of()
            : conversationIds.stream().filter(StringUtils::hasText).distinct().toList();
        if (uniqueConversationIds.isEmpty())
            return;

        fileCleanupTaskMapper.addTasksForConversationReferences(
            uniqueConversationIds,
            userId,
            FileCleanupReason.CONVERSATION_DELETED,
            fileProperties.getOrphanTtlSeconds());
        conversationRoundFileMapper.deleteByConversationIds(uniqueConversationIds, userId);
        fileResourceMapper.clearReservationsForConversations(uniqueConversationIds, userId);
    }

    /**
     * Creates a short-lived signed GET URL for a READY image used in one model request.
     *
     * @param fileResource authorized READY resource
     * @return URL whose expiry is controlled by OSS configuration
     */
    public String createSignedGetUrl(FileResource fileResource)
    {
        Date expiresAt = Date.from(Instant.now().plusSeconds(ossProperties.getPresignedUrlTtlSeconds()));
        return createSignedGetUrl(fileResource, expiresAt);
    }

    /**
     * Maps internal file state to the public metadata response while selecting detected MIME over
     * the user-declared value after content inspection.
     *
     * @param fileResource internal resource entity
     * @return API-safe file metadata
     */
    public FileResourceInfo toFileResourceInfo(FileResource fileResource)
    {
        FileResourceInfo info = new FileResourceInfo();
        BeanUtils.copyProperties(fileResource, info);
        info.setMimeType(StringUtils.hasText(fileResource.getDetectedMimeType())
            ? fileResource.getDetectedMimeType()
            : fileResource.getDeclaredMimeType());
        return info;
    }

    /**
     * Loads one file through the ownership-constrained query used by every file operation.
     *
     * @param fileId stable file reference
     * @param userId authenticated owner
     * @return owned resource
     * @throws ServiceResponseException when the resource is missing or belongs to another user
     */
    private FileResource requireOwnedFile(String fileId, long userId)
    {
        FileResource fileResource = fileResourceMapper.getOwnedFileResource(fileId, userId);
        if (fileResource == null)
            throw new ServiceResponseException(ERROR_FILE_NOT_FOUND, "The file does not exist.");
        return fileResource;
    }

    /**
     * Validates filename/MIME compatibility, configured size limits, and the private-bucket safety
     * invariant before any resource or signed URL is created.
     *
     * @param filename normalized original filename
     * @param extension normalized extension
     * @param mimeType normalized declared MIME type
     * @param fileSize expected upload size in bytes
     */
    private void validateUpload(String filename, String extension, String mimeType, long fileSize)
    {
        if (!StringUtils.hasText(filename) || !StringUtils.hasText(extension))
            throw new ServiceResponseException(ERROR_INVALID_FILE, "A supported filename extension is required.");
        if (!fileProperties.getAllowedExtensions().contains(extension))
            throw new ServiceResponseException(ERROR_INVALID_FILE, "The file extension is not supported.");
        if (!fileProperties.getAllowedMimeTypes().contains(mimeType))
            throw new ServiceResponseException(ERROR_INVALID_FILE, "The file MIME type is not supported.");
        if (!ConversationFileTypeResolver.isMimeTypeCompatible(extension, mimeType))
            throw new ServiceResponseException(
                ERROR_INVALID_FILE, "The file MIME type does not match its filename extension.");
        if (fileSize <= 0 || fileSize > fileProperties.getMaxBytes())
            throw new ServiceResponseException(ERROR_INVALID_FILE, "The file exceeds the configured size limit.");
        if (!StringUtils.hasText(ossProperties.getBucketName())
            || !StringUtils.hasText(ossProperties.getAccessKeyId())
            || !StringUtils.hasText(ossProperties.getAccessKeySecret()))
            throw new IllegalStateException("OSS storage is not configured.");
        if (!ossProperties.isPrivateBucket())
            throw new IllegalStateException("Conversation files require a private OSS bucket.");
    }

    /**
     * Builds the immutable owner/date/file-partitioned OSS key. The layout is
     * {@code prefix/userId/YYYY/MM/fileId/source}: date buckets bound operational listings, while
     * the generated file ID prevents collisions and keeps untrusted filenames out of storage paths.
     *
     * @param userId resource owner
     * @param fileId stable generated file ID
     * @return object key used by both upload and cleanup workers
     */
    private String buildObjectKey(long userId, String fileId)
    {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        return String.format(
            Locale.ROOT,
            OBJECT_KEY_LAYOUT,
            trimSlashes(ossProperties.getObjectPrefix()),
            userId,
            now.getYear(),
            now.getMonthValue(),
            fileId);
    }

    /**
     * Creates the actual OSS GET signature using the expiry selected by the public operation.
     *
     * @param fileResource authorized resource whose object key is signed
     * @param expiresAt exact signature expiry
     * @return signed OSS URL
     */
    private String createSignedGetUrl(FileResource fileResource, Date expiresAt)
    {
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(
            fileResource.getBucketName(), fileResource.getObjectKey(), HttpMethod.GET);
        request.setExpiration(expiresAt);
        return ossClient.generatePresignedUrl(request).toString();
    }

    /**
     * Normalizes configured prefix slashes so the object layout has no accidental empty path
     * segment at either boundary.
     *
     * @param value configured prefix
     * @return prefix without leading/trailing slashes
     */
    private static String trimSlashes(String value)
    {
        return value == null ? "" : value.replaceAll("^/+|/+$", "");
    }
}
