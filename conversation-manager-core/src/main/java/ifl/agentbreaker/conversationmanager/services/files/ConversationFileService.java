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
import ifl.agentbreaker.conversationmanager.domain.constants.ConversationFileKind;
import ifl.agentbreaker.conversationmanager.domain.constants.ConversationFileStatus;
import ifl.agentbreaker.conversationmanager.domain.constants.FileCleanupReason;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.ConfirmFileUploadRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.CreateFileUploadSessionRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.DeleteFileResourceRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.RetryFileProcessingRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.FileDownloadUrl;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.FileResourceInfo;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.FileUploadSession;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.FileResource;
import ifl.agentbreaker.conversationmanager.exceptions.ServiceResponseException;
import ifl.agentbreaker.conversationmanager.support.BusinessIdManager;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
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

@Service
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
    private ConversationFileProperties fileProperties;

    @Autowired
    private OssStorageProperties ossProperties;

    @Autowired
    private OSS ossClient;

    @Autowired
    private TransactionTemplate transactionTemplate;

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

    public ServiceResponse<FileResourceInfo> confirmFileUpload(ConfirmFileUploadRequest request)
    {
        long userId = UserContextService.getCurrentUserId();
        String fileId = request.getFileId();
        FileResource existing = requireOwnedFile(fileId, userId);
        if (existing.getStatus() != ConversationFileStatus.PENDING_UPLOAD)
            return ServiceResponse.buildSuccessResponse(toFileResourceInfo(existing));
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
        return ServiceResponse.buildSuccessResponse(toFileResourceInfo(confirmed));
    }

    public ServiceResponse<FileResourceInfo> getFileResource(String fileId)
    {
        long userId = UserContextService.getCurrentUserId();
        return ServiceResponse.buildSuccessResponse(toFileResourceInfo(requireOwnedFile(fileId, userId)));
    }

    @Transactional(rollbackFor = Exception.class)
    public ServiceResponse<FileResourceInfo> retryFileProcessing(RetryFileProcessingRequest request)
    {
        long userId = UserContextService.getCurrentUserId();
        String fileId = request.getFileId();
        FileResource fileResource = requireOwnedFile(fileId, userId);
        if (fileResource.getStatus() != ConversationFileStatus.FAILED)
            throw new ServiceResponseException(ERROR_INVALID_FILE, "Only a failed file can be retried.");
        if (fileResourceMapper.resetFailedForRetry(fileResource.getId(), userId) != 1)
            throw new ServiceResponseException(ERROR_INVALID_FILE, "The file state changed before retry.");
        fileProcessingTaskMapper.upsertPendingTask(fileResource.getId(), userId);
        return ServiceResponse.buildSuccessResponse(toFileResourceInfo(requireOwnedFile(fileId, userId)));
    }

    @Transactional(rollbackFor = Exception.class)
    public ServiceResponse<Boolean> deleteFileResource(DeleteFileResourceRequest request)
    {
        long userId = UserContextService.getCurrentUserId();
        String fileId = request.getFileId();
        FileResource fileResource = requireOwnedFile(fileId, userId);
        if (fileResourceMapper.requestDelete(fileId, userId) != 1)
            throw new ServiceResponseException(ERROR_FILE_BUSY, "The file is referenced by an active request or conversation.");

        fileProcessingTaskMapper.cancelByFileResourceId(fileResource.getId());
        fileCleanupTaskMapper.addTask(fileResource.getId(), userId, FileCleanupReason.USER_REMOVED, 0);
        return ServiceResponse.buildSuccessResponse(true);
    }

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
     * Batch-loads files owned by the RPC caller and restores the exact order supplied by Agent
     * Runner. The preparation RPC uses this to authorize a frozen attachment selection without an
     * N+1 lookup; a missing result means at least one ID was absent, deleted, or owned by another user.
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
     * Temporarily reserves the selected resources for one chat request so concurrent sends cannot
     * attach the same draft files to different conversations before the Round references are saved.
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

    @Transactional(rollbackFor = Exception.class)
    public void releaseConversationReferences(String conversationId, long userId)
    {
        releaseConversationReferences(List.of(conversationId), userId);
    }

    /**
     * Releases file reservations and durable Round references for multiple conversations as set
     * operations. Cleanup tasks are inserted directly from the relation tables in one SQL statement;
     * no file ID is loaded into Java and no database call occurs inside a loop.
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

    public String createSignedGetUrl(FileResource fileResource)
    {
        Date expiresAt = Date.from(Instant.now().plusSeconds(ossProperties.getPresignedUrlTtlSeconds()));
        return createSignedGetUrl(fileResource, expiresAt);
    }

    public FileResourceInfo toFileResourceInfo(FileResource fileResource)
    {
        FileResourceInfo info = new FileResourceInfo();
        BeanUtils.copyProperties(fileResource, info);
        info.setMimeType(StringUtils.hasText(fileResource.getDetectedMimeType())
            ? fileResource.getDetectedMimeType()
            : fileResource.getDeclaredMimeType());
        return info;
    }

    private FileResource requireOwnedFile(String fileId, long userId)
    {
        FileResource fileResource = fileResourceMapper.getOwnedFileResource(fileId, userId);
        if (fileResource == null)
            throw new ServiceResponseException(ERROR_FILE_NOT_FOUND, "The file does not exist.");
        return fileResource;
    }

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

    private String createSignedGetUrl(FileResource fileResource, Date expiresAt)
    {
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(
            fileResource.getBucketName(), fileResource.getObjectKey(), HttpMethod.GET);
        request.setExpiration(expiresAt);
        return ossClient.generatePresignedUrl(request).toString();
    }

    private static String trimSlashes(String value)
    {
        return value == null ? "" : value.replaceAll("^/+|/+$", "");
    }
}
