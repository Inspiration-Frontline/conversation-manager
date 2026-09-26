package ifl.agentbreaker.conversationmanager.services.files;

import com.aliyun.oss.OSS;
import ifl.agentbreaker.conversationmanager.config.ConversationFileProperties;
import ifl.agentbreaker.conversationmanager.config.OssStorageProperties;
import ifl.agentbreaker.conversationmanager.dao.ConversationGenerationAttemptMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationRoundGeneratedFileMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationRoundMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationTaskAgentExecutionMapper;
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
import ifl.agentbreaker.conversationmanager.exceptions.ServiceResponseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

import org.junit.jupiter.api.Assertions;
import org.mockito.Mockito;

@ExtendWith(MockitoExtension.class)
class GeneratedFileMaterializationServiceTest
{
    /** Owner identity used by every authorized fixture. */
    private static final long USER_ID = 7L;

    /** Stable Conversation identity used by the request fixtures. */
    private static final String CONVERSATION_ID = "conversation-generated";

    /** Round-local source Turn number used by the generation capability. */
    private static final long SOURCE_TURN_NUMBER = 1L;

    /** Fixed generation start timestamp used by deterministic assertions. */
    private static final Instant START_TIME = Instant.parse("2026-09-12T00:00:00Z");

    /** Generation-attempt persistence mock. */
    @Mock
    private ConversationGenerationAttemptMapper conversationGenerationAttemptMapper;

    /** Generated-output relation persistence mock. */
    @Mock
    private ConversationRoundGeneratedFileMapper conversationRoundGeneratedFileMapper;

    /** Conversation ownership persistence mock. */
    @Mock
    private ConversationMapper conversationMapper;

    /** Round ownership persistence mock. */
    @Mock
    private ConversationRoundMapper conversationRoundMapper;

    /** Task-Agent execution persistence mock. */
    @Mock
    private ConversationTaskAgentExecutionMapper conversationTaskAgentExecutionMapper;

    /** Source Turn lookup mock. */
    @Mock
    private ConversationTurnMapper conversationTurnMapper;

    /** Generated file resource persistence mock. */
    @Mock
    private FileResourceMapper fileResourceMapper;

    /** Generated derivative persistence mock. */
    @Mock
    private FileResourceVariantMapper fileResourceVariantMapper;

    /** Image sanitizer mock used after source decoding and metadata validation. */
    @Mock
    private ConversationImageSanitizer conversationImageSanitizer;

    /** Private OSS client mock. */
    @Mock
    private OSS oss;

    /** Transaction boundary mock that executes callbacks synchronously in tests. */
    @Mock
    private TransactionTemplate transactionTemplate;

    /** Service under test. */
    @InjectMocks
    private GeneratedFileMaterializationService service;

    /** File limits used by the service under test. */
    private ConversationFileProperties conversationFileProperties;

    /** OSS settings used for deterministic object-key assertions. */
    private OssStorageProperties ossStorageProperties;

    @BeforeEach
    void setUp()
    {
        conversationFileProperties = new ConversationFileProperties();
        conversationFileProperties.setMaxBytes(10 * 1024 * 1024);
        ossStorageProperties = new OssStorageProperties();
        ossStorageProperties.setBucketName("generated-files");
        ossStorageProperties.setObjectPrefix("test-generated");

        ReflectionTestUtils.setField(service, "conversationFileProperties", conversationFileProperties);
        ReflectionTestUtils.setField(service, "ossStorageProperties", ossStorageProperties);

        Mockito.lenient().when(transactionTemplate.execute(ArgumentMatchers.any())).thenAnswer(invocation ->
        {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(Mockito.mock(TransactionStatus.class));
        });
    }

    @Test
    void rejectsMissingConversationBeforeCreatingAnAttempt()
    {
        Mockito.when(conversationMapper.existsByIdAndUser(CONVERSATION_ID, USER_ID)).thenReturn(false);

        ServiceResponseException error = Assertions.assertThrows(ServiceResponseException.class,
            () -> service.materialize(request(GenerationAttemptStatus.FAILED)));

        Assertions.assertEquals(ConversationFileService.ERROR_FILE_NOT_FOUND, error.getCode());
        Mockito.verify(conversationGenerationAttemptMapper, Mockito.never()).insertOrGet(ArgumentMatchers.any());
    }

    @Test
    void rejectsRoundOwnedByAnotherUser()
    {
        Mockito.when(conversationMapper.existsByIdAndUser(CONVERSATION_ID, USER_ID)).thenReturn(true);
        ConversationRound round = round();
        round.setCreatorId(99L);
        Mockito.when(conversationRoundMapper.getRound(CONVERSATION_ID, 3L)).thenReturn(round);

        ServiceResponseException error = Assertions.assertThrows(ServiceResponseException.class,
            () -> service.materialize(request(GenerationAttemptStatus.FAILED)));

        Assertions.assertEquals(ConversationFileService.ERROR_FILE_NOT_FOUND, error.getCode());
        Mockito.verify(conversationGenerationAttemptMapper, Mockito.never()).insertOrGet(ArgumentMatchers.any());
    }

    @Test
    void rejectsCompletedRequestWithEmptyContentBeforeDatabaseMutation()
    {
        GeneratedFileMaterializationService.MaterializationRequest invalid = withContent(
            request(GenerationAttemptStatus.COMPLETED), new byte[0]);

        ServiceResponseException error = Assertions.assertThrows(ServiceResponseException.class,
            () -> service.materialize(invalid));

        Assertions.assertEquals(GeneratedFileMaterializationService.ERROR_INVALID_GENERATED_FILE, error.getCode());
        Mockito.verifyNoInteractions(conversationMapper, conversationGenerationAttemptMapper, oss);
    }

    @Test
    void rejectsContentOverConfiguredByteLimit()
    {
        conversationFileProperties.setMaxBytes(2L);

        ServiceResponseException error = Assertions.assertThrows(ServiceResponseException.class,
            () -> service.materialize(request(GenerationAttemptStatus.COMPLETED)));

        Assertions.assertEquals(GeneratedFileMaterializationService.ERROR_INVALID_GENERATED_FILE, error.getCode());
        Mockito.verifyNoInteractions(conversationMapper, conversationGenerationAttemptMapper, oss);
    }

    @ParameterizedTest
    @EnumSource(value = GenerationAttemptStatus.class, names = {"FAILED", "CANCELLED", "UNKNOWN"})
    void recordsTerminalNonMaterializedAttemptsWithoutWritingFiles(GenerationAttemptStatus status)
    {
        arrangeAuthorizedRound();
        ConversationTaskAgentExecution execution = new ConversationTaskAgentExecution();
        execution.setId(88L);
        Mockito.when(conversationGenerationAttemptMapper.insertOrGet(ArgumentMatchers.any()))
            .thenAnswer(invocation -> persistedAttempt(invocation.getArgument(0)));
        Mockito.when(conversationTurnMapper.getTurn(42L, SOURCE_TURN_NUMBER)).thenReturn(sourceTurn());
        Mockito.when(conversationTaskAgentExecutionMapper.insertExecution(ArgumentMatchers.any()))
            .thenReturn(execution);
        Mockito.when(conversationTaskAgentExecutionMapper.insertTurnLink(USER_ID, 88L, 77L, 1)).thenReturn(1);

        GeneratedFileMaterializationService.MaterializationResult result = service.materialize(taskRequest(status));

        Assertions.assertEquals(status, result.status());
        Assertions.assertEquals("", result.fileId());
        Assertions.assertEquals(0L, result.fileResourceId());
        Mockito.verifyNoInteractions(oss, fileResourceMapper, fileResourceVariantMapper,
            conversationRoundGeneratedFileMapper);
        ArgumentCaptor<ConversationTaskAgentExecution> captor = ArgumentCaptor.forClass(ConversationTaskAgentExecution.class);
        Mockito.verify(conversationTaskAgentExecutionMapper).insertExecution(captor.capture());
        Assertions.assertEquals(42L, captor.getValue().getRoundId());
        Assertions.assertEquals(77L, captor.getValue().getParentTurnId());
        Assertions.assertEquals(statusToTaskStatus(status), captor.getValue().getStatus());
    }

    @Test
    void materializesOriginalAndDerivativeAndPersistsGenericRelation() throws FileProcessingException
    {
        arrangeAuthorizedRound();
        byte[] content = png(2, 2);
        SanitizedImage sanitized = sanitized(content, 2, 2);
        Mockito.when(conversationImageSanitizer.sanitize(ArgumentMatchers.any(FileResource.class), Mockito.same(content)))
            .thenReturn(sanitized);
        Mockito.when(conversationGenerationAttemptMapper.insertOrGet(ArgumentMatchers.any()))
            .thenAnswer(invocation -> persistedAttempt(invocation.getArgument(0)));
        FileResource persisted = persistedResource();
        Mockito.when(fileResourceMapper.insertGeneratedFileResource(ArgumentMatchers.any())).thenReturn(persisted);
        Mockito.when(fileResourceVariantMapper.upsertPending(ArgumentMatchers.eq(99L), ArgumentMatchers.eq(USER_ID),
            ArgumentMatchers.eq(FileVariantType.MODEL_INPUT), ArgumentMatchers.eq("generated-files"),
            ArgumentMatchers.anyString())).thenReturn(1);
        Mockito.when(fileResourceVariantMapper.markReady(ArgumentMatchers.eq(99L), ArgumentMatchers.eq(USER_ID),
            ArgumentMatchers.eq(FileVariantType.MODEL_INPUT), ArgumentMatchers.eq("image/png"),
            ArgumentMatchers.eq((long) content.length), ArgumentMatchers.eq(sanitized.sha256()), ArgumentMatchers.eq(2),
            ArgumentMatchers.eq(2))).thenReturn(1);
        Mockito.when(conversationRoundGeneratedFileMapper.insertGeneratedFile(ArgumentMatchers.any())).thenReturn(1);

        GeneratedFileMaterializationService.MaterializationResult result = service.materialize(
            withContent(request(GenerationAttemptStatus.COMPLETED), content));

        Assertions.assertEquals("file-generated-1", result.fileId());
        Assertions.assertEquals(99L, result.fileResourceId());
        Assertions.assertEquals(GenerationAttemptStatus.MATERIALIZED, result.status());
        Mockito.verify(oss, Mockito.times(2)).putObject(ArgumentMatchers.eq("generated-files"),
            ArgumentMatchers.anyString(), ArgumentMatchers.<InputStream>any(), ArgumentMatchers.any());
        ArgumentCaptor<ConversationRoundGeneratedFile> relationCaptor =
            ArgumentCaptor.forClass(ConversationRoundGeneratedFile.class);
        Mockito.verify(conversationRoundGeneratedFileMapper).insertGeneratedFile(relationCaptor.capture());
        Assertions.assertEquals(42L, relationCaptor.getValue().getRoundId());
        Assertions.assertEquals(99L, relationCaptor.getValue().getFileResourceId());
        Assertions.assertEquals(GeneratedOutputKind.IMAGE, relationCaptor.getValue().getOutputKind());
        Assertions.assertEquals(GeneratedOutputStatus.ACTIVE, relationCaptor.getValue().getOutputStatus());
    }

    @Test
    void cleansBothObjectsWhenMetadataTransactionFails() throws FileProcessingException
    {
        arrangeAuthorizedRound();
        byte[] content = png(2, 2);
        SanitizedImage sanitized = sanitized(content, 2, 2);
        Mockito.when(conversationImageSanitizer.sanitize(ArgumentMatchers.any(FileResource.class), Mockito.same(content)))
            .thenReturn(sanitized);
        Mockito.when(conversationGenerationAttemptMapper.insertOrGet(ArgumentMatchers.any()))
            .thenAnswer(invocation -> persistedAttempt(invocation.getArgument(0)));
        Mockito.when(fileResourceMapper.insertGeneratedFileResource(ArgumentMatchers.any()))
            .thenThrow(new IllegalStateException("metadata write failed"));
        Mockito.when(oss.doesObjectExist(ArgumentMatchers.eq("generated-files"), ArgumentMatchers.anyString())).thenReturn(true);

        Assertions.assertThrows(IllegalStateException.class, () -> service.materialize(
            withContent(request(GenerationAttemptStatus.COMPLETED), content)));

        Mockito.verify(oss, Mockito.times(2)).deleteObject(ArgumentMatchers.eq("generated-files"),
            ArgumentMatchers.anyString());
        Mockito.verify(conversationRoundGeneratedFileMapper, Mockito.never())
            .insertGeneratedFile(ArgumentMatchers.any());
    }

    @Test
    void replaysExistingMaterializedAttemptWithoutWritingDuplicateObjects()
    {
        ConversationGenerationAttempt existing = new ConversationGenerationAttempt();
        existing.setId(55L);
        existing.setAttemptId("attempt-1");
        existing.setCreatorId(USER_ID);
        existing.setRoundId(42L);
        existing.setStatus(GenerationAttemptStatus.MATERIALIZED);
        ConversationRoundGeneratedFile relation = new ConversationRoundGeneratedFile();
        relation.setFileResourceId(99L);
        relation.setOutputStatus(GeneratedOutputStatus.ACTIVE);
        FileResource resource = persistedResource();
        Mockito.when(conversationMapper.existsByIdAndUser(CONVERSATION_ID, USER_ID)).thenReturn(true);
        Mockito.when(conversationRoundMapper.getRound(CONVERSATION_ID, 3L)).thenReturn(round());
        Mockito.when(conversationGenerationAttemptMapper.getByAttemptId("attempt-1")).thenReturn(existing);
        Mockito.when(conversationRoundGeneratedFileMapper.getByGenerationAttemptId(55L)).thenReturn(relation);
        Mockito.when(fileResourceMapper.getFileResourceById(99L)).thenReturn(resource);

        GeneratedFileMaterializationService.MaterializationResult result = service.materialize(request(
            GenerationAttemptStatus.COMPLETED));

        Assertions.assertEquals("file-generated-1", result.fileId());
        Assertions.assertEquals(55L, result.generationAttemptId());
        Mockito.verifyNoInteractions(oss);
        Mockito.verify(conversationRoundMapper).getRound(CONVERSATION_ID, 3L);
        Mockito.verify(conversationMapper).existsByIdAndUser(CONVERSATION_ID, USER_ID);
        Mockito.verify(conversationGenerationAttemptMapper, Mockito.never()).insertOrGet(ArgumentMatchers.any());
    }

    @Test
    void persistsTaskExecutionAndLinksItToTheSourceTurn() throws FileProcessingException
    {
        arrangeAuthorizedRound();
        byte[] content = png(2, 2);
        SanitizedImage sanitized = sanitized(content, 2, 2);
        Mockito.when(conversationImageSanitizer.sanitize(ArgumentMatchers.any(FileResource.class), Mockito.same(content)))
            .thenReturn(sanitized);
        Mockito.when(conversationGenerationAttemptMapper.insertOrGet(ArgumentMatchers.any()))
            .thenAnswer(invocation -> persistedAttempt(invocation.getArgument(0)));
        Mockito.when(conversationTurnMapper.getTurn(42L, SOURCE_TURN_NUMBER)).thenReturn(sourceTurn());
        ConversationTaskAgentExecution execution = new ConversationTaskAgentExecution();
        execution.setId(88L);
        Mockito.when(conversationTaskAgentExecutionMapper.insertExecution(ArgumentMatchers.any())).thenReturn(execution);
        Mockito.when(conversationTaskAgentExecutionMapper.insertTurnLink(USER_ID, 88L, 77L, 1)).thenReturn(1);
        Mockito.when(fileResourceMapper.insertGeneratedFileResource(ArgumentMatchers.any())).thenReturn(persistedResource());
        Mockito.when(fileResourceVariantMapper.upsertPending(ArgumentMatchers.eq(99L), ArgumentMatchers.eq(USER_ID),
            ArgumentMatchers.eq(FileVariantType.MODEL_INPUT), ArgumentMatchers.eq("generated-files"),
            ArgumentMatchers.anyString())).thenReturn(1);
        Mockito.when(fileResourceVariantMapper.markReady(ArgumentMatchers.eq(99L), ArgumentMatchers.eq(USER_ID),
            ArgumentMatchers.eq(FileVariantType.MODEL_INPUT), ArgumentMatchers.eq("image/png"),
            ArgumentMatchers.eq((long) content.length), ArgumentMatchers.eq(sanitized.sha256()), ArgumentMatchers.eq(2),
            ArgumentMatchers.eq(2))).thenReturn(1);
        Mockito.when(conversationRoundGeneratedFileMapper.insertGeneratedFile(ArgumentMatchers.any())).thenReturn(1);

        service.materialize(withContent(taskRequest(GenerationAttemptStatus.COMPLETED), content));

        ArgumentCaptor<ConversationTaskAgentExecution> captor = ArgumentCaptor.forClass(ConversationTaskAgentExecution.class);
        Mockito.verify(conversationTaskAgentExecutionMapper).insertExecution(captor.capture());
        Assertions.assertEquals(2L, captor.getValue().getTaskAgentId());
        Assertions.assertEquals("builtin.generate_image", captor.getValue().getCapabilityKey());
        Assertions.assertEquals(77L, captor.getValue().getParentTurnId());
        Mockito.verify(conversationTaskAgentExecutionMapper).insertTurnLink(USER_ID, 88L, 77L, 1);
    }

    private GeneratedFileMaterializationService.MaterializationRequest request(GenerationAttemptStatus status)
    {
        return new GeneratedFileMaterializationService.MaterializationRequest(
            USER_ID, CONVERSATION_ID, 3L, "attempt-1", "builtin.generate_image",
            "doubao-seedream-4-0-250828", status, SOURCE_TURN_NUMBER, "generated.png", "image/png",
            png(2, 2), "", 2, 2, GeneratedOutputKind.IMAGE, "draw a blue cat", "provider-1", "", "",
            START_TIME, START_TIME.plusSeconds(5), "request-1", "trace-1", 0L, "", 0, "{}", "", "");
    }

    private GeneratedFileMaterializationService.MaterializationRequest taskRequest(GenerationAttemptStatus status)
    {
        GeneratedFileMaterializationService.MaterializationRequest base = request(status);
        return new GeneratedFileMaterializationService.MaterializationRequest(
            base.userId(), base.conversationId(), base.roundNumber(), base.attemptId(), base.capabilityKey(),
            base.model(), base.status(), base.sourceTurnNumber(), base.originalFilename(), base.mimeType(),
            base.content(), base.sha256(), base.width(), base.height(), base.outputKind(), base.rewrittenInstruction(),
            base.providerRequestId(), base.errorCode(), base.errorMessage(), base.startTime(), base.endTime(),
            base.requestId(), base.traceId(), 2L, "image-generation-agent", 1, "{}", "parent-span", "task-span");
    }

    private GeneratedFileMaterializationService.MaterializationRequest withContent(
        GeneratedFileMaterializationService.MaterializationRequest request, byte[] content)
    {
        return new GeneratedFileMaterializationService.MaterializationRequest(
            request.userId(), request.conversationId(), request.roundNumber(), request.attemptId(), request.capabilityKey(),
            request.model(), request.status(), request.sourceTurnNumber(), request.originalFilename(), request.mimeType(),
            content, request.sha256(), request.width(), request.height(), request.outputKind(), request.rewrittenInstruction(),
            request.providerRequestId(), request.errorCode(), request.errorMessage(), request.startTime(), request.endTime(),
            request.requestId(), request.traceId(), request.taskAgentId(), request.taskAgentName(), request.taskAgentVersion(),
            request.normalizedSettingsJson(), request.parentSpanId(), request.taskSpanId());
    }

    private ConversationRound round()
    {
        ConversationRound round = new ConversationRound();
        round.setId(42L);
        round.setCreatorId(USER_ID);
        round.setRoundNumber(3L);
        return round;
    }

    private ConversationTurn sourceTurn()
    {
        ConversationTurn turn = new ConversationTurn();
        turn.setId(77L);
        return turn;
    }

    private void arrangeAuthorizedRound()
    {
        Mockito.when(conversationMapper.existsByIdAndUser(CONVERSATION_ID, USER_ID)).thenReturn(true);
        Mockito.when(conversationRoundMapper.getRound(CONVERSATION_ID, 3L)).thenReturn(round());
    }

    private ConversationGenerationAttempt persistedAttempt(ConversationGenerationAttempt candidate)
    {
        ConversationGenerationAttempt attempt = new ConversationGenerationAttempt();
        attempt.setId(55L);
        attempt.setCreatorId(candidate.getCreatorId());
        attempt.setRoundId(candidate.getRoundId());
        attempt.setAttemptId(candidate.getAttemptId());
        attempt.setStatus(candidate.getStatus());
        return attempt;
    }

    private FileResource persistedResource()
    {
        FileResource resource = new FileResource();
        resource.setId(99L);
        resource.setFileId("file-generated-1");
        resource.setOrigin(FileResourceOrigin.GENERATED);
        resource.setKind(ConversationFileKind.IMAGE);
        resource.setStatus(ConversationFileStatus.READY);
        resource.setBucketName("generated-files");
        return resource;
    }

    private SanitizedImage sanitized(byte[] content, int width, int height)
    {
        return new SanitizedImage(content, "image/png", "png", sha256(content), width, height, width, height);
    }

    private byte[] png(int width, int height)
    {
        try
        {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Assertions.assertTrue(ImageIO.write(image, "png", output));
            return output.toByteArray();
        }
        catch (Exception error)
        {
            throw new IllegalStateException("Could not build test image.", error);
        }
    }

    private String sha256(byte[] content)
    {
        try
        {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        }
        catch (Exception error)
        {
            throw new IllegalStateException("Could not hash test image.", error);
        }
    }

    private TaskAgentExecutionStatus statusToTaskStatus(GenerationAttemptStatus status)
    {
        return switch (status)
        {
            case FAILED -> TaskAgentExecutionStatus.FAILED;
            case CANCELLED -> TaskAgentExecutionStatus.CANCELLED;
            case UNKNOWN -> TaskAgentExecutionStatus.UNKNOWN;
            default -> TaskAgentExecutionStatus.RUNNING;
        };
    }
}
