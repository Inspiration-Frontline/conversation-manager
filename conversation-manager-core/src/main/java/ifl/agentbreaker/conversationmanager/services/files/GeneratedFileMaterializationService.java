package ifl.agentbreaker.conversationmanager.services.files;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import ifl.agentbreaker.conversationmanager.config.ConversationFileProperties;
import ifl.agentbreaker.conversationmanager.config.OssStorageProperties;
import ifl.agentbreaker.conversationmanager.dao.ConversationGenerationAttemptMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationRoundGeneratedFileMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationRoundMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationTaskAgentExecutionMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationTurnMapper;
import ifl.agentbreaker.conversationmanager.dao.FileResourceMapper;
import ifl.agentbreaker.conversationmanager.dao.FileResourceVariantMapper;
import ifl.agentbreaker.conversationmanager.domain.constants.ConversationFileKind;
import ifl.agentbreaker.conversationmanager.domain.constants.ConversationFileStatus;
import ifl.agentbreaker.conversationmanager.domain.constants.FileResourceOrigin;
import ifl.agentbreaker.conversationmanager.domain.constants.FileVariantType;
import ifl.agentbreaker.conversationmanager.domain.constants.GeneratedOutputKind;
import ifl.agentbreaker.conversationmanager.domain.constants.GeneratedOutputStatus;
import ifl.agentbreaker.conversationmanager.domain.constants.GenerationAttemptStatus;
import ifl.agentbreaker.conversationmanager.domain.constants.TaskAgentExecutionStatus;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationGenerationAttempt;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationRound;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationRoundGeneratedFile;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationTaskAgentExecution;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationTurn;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.FileResource;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.GenerationAttemptTerminalUpdate;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.GeneratedFileMaterializationRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.MaterializedGeneratedFile;
import ifl.agentbreaker.conversationmanager.exceptions.ServiceResponseException;
import ifl.agentbreaker.conversationmanager.support.BusinessIdManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

/**
 * Materializes validated Runner output into an authorized, durable file resource.
 */
@Service
public class GeneratedFileMaterializationService
{
    /**
     * Client-visible validation code for generated output requests.
     */
    public static final int ERROR_INVALID_GENERATED_FILE = 2400;

    /**
     * Ownership and Conversation lookup.
     */
    @Autowired
    private ConversationMapper conversationMapper;

    /**
     * Round lookup by stable Conversation boundary.
     */
    @Autowired
    private ConversationRoundMapper conversationRoundMapper;

    /**
     * Idempotent attempt persistence.
     */
    @Autowired
    private ConversationGenerationAttemptMapper conversationGenerationAttemptMapper;

    /**
     * Durable generated relation persistence.
     */
    @Autowired
    private ConversationRoundGeneratedFileMapper conversationRoundGeneratedFileMapper;

    /**
     * Optional Task-Agent diagnostic persistence.
     */
    @Autowired
    private ConversationTaskAgentExecutionMapper conversationTaskAgentExecutionMapper;

    /**
     * Persisted Primary-Agent Turn lookup used to anchor Task-Agent diagnostics.
     */
    @Autowired
    private ConversationTurnMapper conversationTurnMapper;

    /**
     * File resource persistence.
     */
    @Autowired
    private FileResourceMapper fileResourceMapper;

    /**
     * Sanitized preview/model-input derivative persistence.
     */
    @Autowired
    private FileResourceVariantMapper fileResourceVariantMapper;

    /**
     * Image decoding and derivative validation.
     */
    @Autowired
    private ConversationImageSanitizer conversationImageSanitizer;

    /**
     * Private object storage client.
     */
    @Autowired
    private OSS oss;

    /**
     * Storage configuration.
     */
    @Autowired
    private OssStorageProperties ossStorageProperties;

    /**
     * Output limits shared with uploaded image processing.
     */
    @Autowired
    private ConversationFileProperties conversationFileProperties;

    /**
     * Transaction boundary for durable metadata and relation rows.
     */
    @Autowired
    private TransactionTemplate transactionTemplate;

    /**
     * Materializes one terminal generation attempt and returns its stable file reference.
     */
    public MaterializationResult materialize(GeneratedFileMaterializationRequest request)
    {
        validateRequest(request);

        if (!conversationMapper.existsByIdAndUser(request.conversationId(), request.userId()))
            throw new ServiceResponseException(ConversationFileService.ERROR_FILE_NOT_FOUND, "Conversation does not exist.");

        ConversationRound round = conversationRoundMapper.getRound(request.conversationId(), request.roundNumber());

        if (round == null || round.isDeleted() || round.getCreatorId() != request.userId())
            throw new ServiceResponseException(ConversationFileService.ERROR_FILE_NOT_FOUND, "Round does not exist.");

        ConversationGenerationAttempt existingGenerationAttempt = conversationGenerationAttemptMapper.getByAttemptId(request.attemptId());

        if (existingGenerationAttempt != null)
            return replayOrAdvanceExisting(request, existingGenerationAttempt);

        ConversationGenerationAttempt attempt = createAttempt(request, round.getId());
        ConversationGenerationAttempt inserted = conversationGenerationAttemptMapper.insertOrGet(attempt);

        if (inserted == null)
            throw new IllegalStateException("The generation attempt could not be persisted.");

        if (!inserted.getAttemptId().equals(request.attemptId()))
            return replayOrAdvanceExisting(request, inserted);

        if (request.status() != GenerationAttemptStatus.COMPLETED)
        {
            persistTaskExecution(request, round.getId(), inserted.getId());
            return new MaterializationResult(request.attemptId(), inserted.getId(), "", 0,
                GeneratedOutputStatus.ACTIVE, request.status());
        }

        return materializeCompleted(request, round, inserted);
    }

    /**
     * Replays an idempotent result or continues a previously accepted materialization.
     */
    private MaterializationResult replayOrAdvanceExisting(GeneratedFileMaterializationRequest request,
                                                          ConversationGenerationAttempt existingGenerationAttempt)
    {
        if (existingGenerationAttempt.getCreatorId() != request.userId() || existingGenerationAttempt.getRoundId() <= 0)
            throw new ServiceResponseException(ConversationFileService.ERROR_FILE_NOT_FOUND, "Generation attempt does not exist.");

        MaterializedGeneratedFile materialized = conversationRoundGeneratedFileMapper
            .getMaterializedFileByAttemptId(existingGenerationAttempt.getId());

        if (materialized != null)
            return new MaterializationResult(existingGenerationAttempt.getAttemptId(), existingGenerationAttempt.getId(),
                materialized.fileId(), materialized.fileResourceId(),
                GeneratedOutputStatus.valueOf(materialized.outputStatus()), existingGenerationAttempt.getStatus());

        if (request.status() != GenerationAttemptStatus.COMPLETED)
        {
            conversationGenerationAttemptMapper.updateTerminal(
                terminalUpdate(request, request.status().name(), request.errorCode(), request.errorMessage()));
            return new MaterializationResult(existingGenerationAttempt.getAttemptId(), existingGenerationAttempt.getId(), "", 0,
                GeneratedOutputStatus.ACTIVE, request.status());
        }

        ConversationRound round = conversationRoundMapper.getRound(request.conversationId(), request.roundNumber());

        if (round == null)
            throw new ServiceResponseException(ConversationFileService.ERROR_FILE_NOT_FOUND, "Round does not exist.");

        return materializeCompleted(request, round, existingGenerationAttempt);
    }

    /**
     * Performs bounded image validation, OSS publication, and one transaction of metadata writes.
     */
    private MaterializationResult materializeCompleted(GeneratedFileMaterializationRequest request,
                                                       ConversationRound round,
                                                       ConversationGenerationAttempt attempt)
    {
        ValidatedImage validated = validateImage(request);
        FileResource resource = createResource(request, validated, round.getCreatorId());
        String derivativeKey = DerivativeObjectKeyBuilder.build(resource, validated.sanitized().extension());

        try
        {
            putObject(resource.getBucketName(), resource.getObjectKey(), request.content(), request.mimeType());
            putObject(resource.getBucketName(), derivativeKey, validated.sanitized().bytes(), validated.sanitized().mimeType());
            MaterializationResult result = transactionTemplate.execute(status -> persistMaterializedRows(
                request, round, attempt, resource, validated, derivativeKey));

            if (result == null)
                throw new IllegalStateException("Generated file persistence returned no result.");

            return result;
        }
        catch (RuntimeException error)
        {
            deleteObjectQuietly(resource.getBucketName(), resource.getObjectKey());
            deleteObjectQuietly(resource.getBucketName(), derivativeKey);
            throw error;
        }
    }

    /**
     * Persists resource, derivative, relation, attempt, and optional Task-Agent detail atomically.
     */
    private MaterializationResult persistMaterializedRows(GeneratedFileMaterializationRequest request,
                                                          ConversationRound round,
                                                          ConversationGenerationAttempt attempt,
                                                          FileResource resource,
                                                          ValidatedImage validated,
                                                          String derivativeKey)
    {
        FileResource insertedResource = fileResourceMapper.insertGeneratedFileResource(resource);

        if (insertedResource == null)
            throw new IllegalStateException("The generated file resource could not be persisted.");

        if (fileResourceVariantMapper.upsertPending(insertedResource.getId(), request.userId(),
            FileVariantType.MODEL_INPUT, insertedResource.getBucketName(), derivativeKey) != 1
            || fileResourceVariantMapper.markReady(insertedResource.getId(), request.userId(), FileVariantType.MODEL_INPUT,
            validated.sanitized().mimeType(), validated.sanitized().bytes().length, validated.sanitized().sha256(),
            validated.sanitized().width(), validated.sanitized().height()) != 1)
            throw new IllegalStateException("The generated preview variant could not be persisted.");

        ConversationRoundGeneratedFile relation = new ConversationRoundGeneratedFile();
        relation.setCreatorId(request.userId());
        relation.setModifierId(request.userId());
        relation.setRoundId(round.getId());
        relation.setFileResourceId(insertedResource.getId());
        relation.setSourceTurnNumber(request.sourceTurnNumber());
        relation.setGenerationAttemptId(attempt.getId());
        relation.setOutputKind(request.outputKind());
        relation.setOutputStatus(GeneratedOutputStatus.ACTIVE);

        if (conversationRoundGeneratedFileMapper.insertGeneratedFile(relation) != 1)
            throw new IllegalStateException("The generated output relation could not be persisted.");

        conversationGenerationAttemptMapper.updateTerminal(terminalUpdate(request,
            GenerationAttemptStatus.MATERIALIZED.name(), "", ""));
        persistTaskExecution(request, round.getId(), attempt.getId());

        return new MaterializationResult(request.attemptId(), attempt.getId(), insertedResource.getFileId(),
            insertedResource.getId(), GeneratedOutputStatus.ACTIVE, GenerationAttemptStatus.MATERIALIZED);
    }

    /**
     * Creates an optional Task-Agent execution row from the request audit fields.
     */
    private void persistTaskExecution(GeneratedFileMaterializationRequest request, long roundId, long attemptId)
    {
        if (request.taskAgentId() <= 0 || !StringUtils.hasText(request.taskAgentName()))
            return;

        ConversationTurn sourceTurn = conversationTurnMapper.getTurn(roundId, request.sourceTurnNumber());

        if (sourceTurn == null)
            throw new IllegalStateException("The source Turn for the Task-Agent execution could not be found.");

        ConversationTaskAgentExecution execution = new ConversationTaskAgentExecution();
        execution.setCreatorId(request.userId());
        execution.setModifierId(request.userId());
        execution.setRoundId(roundId);
        execution.setTaskAgentId(request.taskAgentId());
        execution.setTaskAgentName(request.taskAgentName());
        execution.setTaskAgentVersion(Math.max(1, request.taskAgentVersion()));
        execution.setCapabilityKey(request.capabilityKey());
        execution.setStatus(toTaskStatus(request.status()));
        execution.setRequestId(request.requestId());
        execution.setTraceId(request.traceId());
        execution.setParentSpanId(request.parentSpanId());
        execution.setTaskSpanId(request.taskSpanId());
        execution.setRewrittenInstruction(request.rewrittenInstruction());
        execution.setInputResourceIdsJson("[]");
        execution.setNormalizedSettingsJson(StringUtils.hasText(request.normalizedSettingsJson())
            ? request.normalizedSettingsJson() : "{}");
        execution.setGenerationAttemptId(attemptId);
        execution.setProviderRequestId(request.providerRequestId());
        execution.setErrorCode(request.errorCode());
        execution.setErrorMessage(request.errorMessage());
        execution.setStartTime(request.startTime());
        execution.setEndTime(request.endTime());
        execution.setParentTurnId(sourceTurn.getId());
        ConversationTaskAgentExecution persistedExecution = conversationTaskAgentExecutionMapper.insertExecution(execution);

        if (persistedExecution == null || persistedExecution.getId() <= 0)
            throw new IllegalStateException("The Task-Agent execution could not be persisted.");

        if (conversationTaskAgentExecutionMapper.insertTurnLink(request.userId(), persistedExecution.getId(),
            sourceTurn.getId(), 1) != 1)
            throw new IllegalStateException("The Task-Agent Turn link could not be persisted.");
    }

    /**
     * Maps generation state to Task-Agent state without exposing provider-specific values.
     */
    private TaskAgentExecutionStatus toTaskStatus(GenerationAttemptStatus status)
    {
        return switch (status)
        {
            case COMPLETED, MATERIALIZED -> TaskAgentExecutionStatus.COMPLETED;
            case CANCELLED -> TaskAgentExecutionStatus.CANCELLED;
            case UNKNOWN -> TaskAgentExecutionStatus.UNKNOWN;
            case FAILED -> TaskAgentExecutionStatus.FAILED;
            default -> TaskAgentExecutionStatus.RUNNING;
        };
    }

    /**
     * Validates request identity and content bounds before any OSS or database mutation.
     */
    private void validateRequest(GeneratedFileMaterializationRequest request)
    {
        if (request.userId() <= 0 || !StringUtils.hasText(request.conversationId()) || request.roundNumber() <= 0
            || !StringUtils.hasText(request.attemptId()) || request.attemptId().length() > 100
            || request.sourceTurnNumber() <= 0 || request.outputKind() == null)
            throw new ServiceResponseException(ERROR_INVALID_GENERATED_FILE, "The generated file request is invalid.");

        if (request.startTime() == null || (request.endTime() != null && request.endTime().isBefore(request.startTime())))
            throw new ServiceResponseException(ERROR_INVALID_GENERATED_FILE, "The generation timestamps are invalid.");

        if (request.status() == GenerationAttemptStatus.COMPLETED
            && (request.content() == null || request.content().length == 0))
            throw new ServiceResponseException(ERROR_INVALID_GENERATED_FILE, "Generated content is required for a completed attempt.");

        if (request.content() != null && request.content().length > conversationFileProperties.getMaxBytes())
            throw new ServiceResponseException(ERROR_INVALID_GENERATED_FILE, "Generated content exceeds the configured byte limit.");
    }

    /**
     * Validates image decoding and creates the sanitized preview derivative.
     */
    private ValidatedImage validateImage(GeneratedFileMaterializationRequest request)
    {
        if (request.outputKind() != GeneratedOutputKind.IMAGE
            || !Set.of("image/png", "image/jpeg", "image/webp").contains(request.mimeType()))
            throw new ServiceResponseException(ERROR_INVALID_GENERATED_FILE, "Only supported still images can be generated.");

        try
        {
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(request.content()));

            if (decoded == null || decoded.getWidth() <= 0 || decoded.getHeight() <= 0)
                throw new IllegalArgumentException("Generated content is not a decodable image.");

            if (request.width() > 0 && request.width() != decoded.getWidth()
                || request.height() > 0 && request.height() != decoded.getHeight())
                throw new IllegalArgumentException("Generated image dimensions do not match the metadata.");

            String extension = extensionForMime(request.mimeType());
            FileResource candidate = new FileResource();
            candidate.setFileExtension(extension);
            candidate.setKind(ConversationFileKind.IMAGE);
            SanitizedImage sanitized = conversationImageSanitizer.sanitize(candidate, request.content());
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(request.content()));

            if (StringUtils.hasText(request.sha256()) && !digest.equalsIgnoreCase(request.sha256()))
                throw new IllegalArgumentException("Generated content checksum does not match the metadata.");

            return new ValidatedImage(decoded.getWidth(), decoded.getHeight(), digest, sanitized);
        }
        catch (ServiceResponseException error)
        {
            throw error;
        }
        catch (Exception error)
        {
            throw new ServiceResponseException(ERROR_INVALID_GENERATED_FILE, "Generated content is not a valid image.");
        }
    }

    /**
     * Creates an immutable READY resource metadata row before transaction insertion.
     */
    private FileResource createResource(GeneratedFileMaterializationRequest request, ValidatedImage validated, long userId)
    {
        String fileId = BusinessIdManager.newFileId();
        String extension = extensionForMime(request.mimeType());
        Instant now = Instant.now();
        FileResource resource = new FileResource();
        resource.setCreatorId(userId);
        resource.setModifierId(userId);
        resource.setFileId(fileId);
        resource.setKind(ConversationFileKind.IMAGE);
        resource.setOrigin(FileResourceOrigin.GENERATED);
        resource.setStatus(ConversationFileStatus.READY);
        resource.setStatusRevision(1);
        resource.setBucketName(ossStorageProperties.getBucketName());
        resource.setObjectKey(buildSourceKey(userId, fileId));
        resource.setOriginalFilename(StringUtils.hasText(request.originalFilename())
            ? ConversationFileTypeResolver.normalizeFilename(request.originalFilename()) : "generated." + extension);
        resource.setFileExtension(extension);
        resource.setDeclaredMimeType(request.mimeType());
        resource.setDetectedMimeType(request.mimeType());
        resource.setFileSize(request.content().length);
        resource.setSha256(validated.sha256());
        resource.setWidth(validated.width());
        resource.setHeight(validated.height());
        resource.setUploadExpiresAt(now);
        resource.setConfirmedTime(now);
        resource.setReadyTime(now);
        resource.setOrphanedTime(now);
        return resource;
    }

    /**
     * Creates a generation attempt entity from bounded request metadata.
     */
    private ConversationGenerationAttempt createAttempt(GeneratedFileMaterializationRequest request, long roundId)
    {
        ConversationGenerationAttempt attempt = new ConversationGenerationAttempt();
        attempt.setCreatorId(request.userId());
        attempt.setModifierId(request.userId());
        attempt.setRoundId(roundId);
        attempt.setAttemptId(request.attemptId());
        attempt.setCapabilityKey(request.capabilityKey());
        attempt.setModel(request.model());
        attempt.setStatus(request.status());
        attempt.setProviderRequestId(request.providerRequestId());
        attempt.setErrorCode(request.errorCode());
        attempt.setErrorMessage(request.errorMessage());
        attempt.setStartTime(request.startTime());
        attempt.setEndTime(request.endTime());
        return attempt;
    }

    /**
     * Publishes one immutable object with a bounded content type.
     */
    private void putObject(String bucketName, String objectKey, byte[] content, String mimeType)
    {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(mimeType);
        metadata.setContentLength(content.length);
        oss.putObject(bucketName, objectKey, new ByteArrayInputStream(content), metadata);
    }

    /**
     * Deletes a crash-left object without masking the original persistence failure.
     */
    private void deleteObjectQuietly(String bucketName, String objectKey)
    {
        try
        {
            if (oss.doesObjectExist(bucketName, objectKey))
                oss.deleteObject(bucketName, objectKey);
        }
        catch (Exception ignored)
        {
            // Cleanup workers can recover an object if the immediate best-effort delete fails.
        }
    }

    /**
     * Builds a stable source object key that never contains the untrusted display filename.
     */
    private String buildSourceKey(long userId, String fileId)
    {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        String prefix = ossStorageProperties.getObjectPrefix() == null ? "" : ossStorageProperties.getObjectPrefix().replaceAll("^/+|/+$", "");
        return String.format(Locale.ROOT, "%s/%d/%04d/%02d/%s/source", prefix, userId,
            now.getYear(), now.getMonthValue(), fileId);
    }

    /**
     * Builds the deterministic derivative key adjacent to the source object.
     */
    /**
     * Maps a supported image MIME to its normalized extension.
     */
    private String extensionForMime(String mimeType)
    {
        return switch (mimeType)
        {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
    }

    /**
     * Validated source and sanitized derivative metadata.
     */
    private record ValidatedImage(int width, int height, String sha256, SanitizedImage sanitized)
    {
    }

    /**
     * Stable output returned to the RPC boundary after idempotent persistence.
     */
    public record MaterializationResult(String attemptId,
                                        long generationAttemptId,
                                        String fileId,
                                        long fileResourceId,
                                        GeneratedOutputStatus outputStatus,
                                        GenerationAttemptStatus status)
    {
    }

    /**
     * Builds the terminal mutation for one attempt from the request's audit fields.
     *
     * @param request materialization request carrying attempt identity and provider metadata
     * @param status terminal attempt status to persist
     * @param errorCode client-safe failure classification, empty on success
     * @param errorMessage client-safe failure message, empty on success
     * @return populated terminal update for the attempt mapper
     */
    private GenerationAttemptTerminalUpdate terminalUpdate(GeneratedFileMaterializationRequest request, String status,
                                                           String errorCode, String errorMessage)
    {
        GenerationAttemptTerminalUpdate update = new GenerationAttemptTerminalUpdate();
        update.setAttemptId(request.attemptId());
        update.setUserId(request.userId());
        update.setStatus(status);
        update.setProviderRequestId(request.providerRequestId());
        update.setErrorCode(errorCode);
        update.setErrorMessage(errorMessage);
        update.setEndTime(request.endTime());

        return update;
    }
}
