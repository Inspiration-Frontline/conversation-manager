package ifl.agentbreaker.conversationmanager.services.files;

import com.aliyun.oss.OSS;
import ifl.agentbreaker.conversationmanager.config.ConversationFileProperties;
import ifl.agentbreaker.conversationmanager.config.OssStorageProperties;
import ifl.agentbreaker.conversationmanager.dao.ConversationGenerationAttemptMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationRoundEditSourceMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationRoundInputFileMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationRoundGeneratedFileMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationRoundMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationTaskAgentExecutionMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationTurnMapper;
import ifl.agentbreaker.conversationmanager.dao.FileResourceMapper;
import ifl.agentbreaker.conversationmanager.dao.FileResourceVariantMapper;
import ifl.agentbreaker.conversationmanager.domain.constants.ConversationFileKind;
import ifl.agentbreaker.conversationmanager.domain.constants.ConversationFileStatus;
import ifl.agentbreaker.conversationmanager.domain.constants.EditResolutionKind;
import ifl.agentbreaker.conversationmanager.domain.constants.EditSourceKind;
import ifl.agentbreaker.conversationmanager.domain.constants.FileResourceOrigin;
import ifl.agentbreaker.conversationmanager.domain.constants.FileVariantType;
import ifl.agentbreaker.conversationmanager.domain.constants.GeneratedOutputKind;
import ifl.agentbreaker.conversationmanager.domain.constants.GeneratedOutputStatus;
import ifl.agentbreaker.conversationmanager.domain.constants.GenerationAttemptStatus;
import ifl.agentbreaker.conversationmanager.domain.constants.ResolverExecutionStatus;
import ifl.agentbreaker.conversationmanager.domain.constants.TaskAgentExecutionStatus;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.MaterializedGeneratedFile;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationGenerationAttempt;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationRound;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationRoundEditSource;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationRoundGeneratedFile;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationTaskAgentExecution;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationTurn;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.FileResource;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.GeneratedFileMaterializationRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.ReferenceResolutionAudit;
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

    /** Uploaded-source visibility lookup mock. */
    @Mock
    private ConversationRoundInputFileMapper conversationRoundFileMapper;

    /** Edit-source provenance persistence mock. */
    @Mock
    private ConversationRoundEditSourceMapper conversationRoundEditSourceMapper;

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
        GeneratedFileMaterializationRequest invalid = withContent(
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
        Mockito.when(conversationMapper.existsByIdAndUser(CONVERSATION_ID, USER_ID)).thenReturn(true);
        Mockito.when(conversationRoundMapper.getRound(CONVERSATION_ID, 3L)).thenReturn(round());
        Mockito.when(conversationGenerationAttemptMapper.getByAttemptId("attempt-1")).thenReturn(existing);
        Mockito.when(conversationRoundGeneratedFileMapper.getMaterializedFileByAttemptId(55L))
            .thenReturn(new MaterializedGeneratedFile("file-generated-1", 99L, GeneratedOutputStatus.ACTIVE.name()));

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

    @Test
    void rejectsEditResolutionKindWithoutSource()
    {
        Assertions.assertThrows(ServiceResponseException.class, () -> service.materialize(
            editRequest(GenerationAttemptStatus.FAILED, EditResolutionKind.REFERENCE_RESOLVER, "", null)));
        Mockito.verifyNoInteractions(conversationMapper, conversationGenerationAttemptMapper, oss);
    }

    @Test
    void persistsExplicitEditSourceProvenanceForCompletedEdit() throws FileProcessingException
    {
        byte[] content = arrangeCompletedMaterialization();
        FileResource source = sourceResource();
        Mockito.when(fileResourceMapper.getOwnedFileResource("file-source", USER_ID)).thenReturn(source);
        Mockito.when(conversationRoundGeneratedFileMapper.findLatestVisibleRoundNumber(CONVERSATION_ID, 55L))
            .thenReturn(9L);
        ConversationRound sourceRound = round();
        sourceRound.setId(41L);
        Mockito.when(conversationRoundMapper.getRound(CONVERSATION_ID, 9L)).thenReturn(sourceRound);

        service.materialize(withEditContent(
            editRequest(GenerationAttemptStatus.COMPLETED, EditResolutionKind.EXPLICIT, "file-source", null),
            content));

        ArgumentCaptor<ConversationRoundEditSource> captor = ArgumentCaptor.forClass(ConversationRoundEditSource.class);
        Mockito.verify(conversationRoundEditSourceMapper).insertEditSource(captor.capture());
        Assertions.assertEquals(42L, captor.getValue().getRoundId());
        Assertions.assertEquals(55L, captor.getValue().getSourceFileResourceId());
        Assertions.assertEquals(EditSourceKind.GENERATED, captor.getValue().getSourceKind());
        Assertions.assertEquals(41L, captor.getValue().getSourceRoundId());
        Assertions.assertEquals(EditResolutionKind.EXPLICIT, captor.getValue().getResolutionKind());
        Assertions.assertNull(captor.getValue().getResolverExecutionId());
        Mockito.verify(conversationTaskAgentExecutionMapper, Mockito.times(1))
            .insertExecution(ArgumentMatchers.any());
    }

    @Test
    void persistsResolverAuditAndLinksProvenanceForResolverChosenEdit() throws FileProcessingException
    {
        byte[] content = arrangeCompletedMaterialization();
        ConversationTaskAgentExecution resolverExecution = new ConversationTaskAgentExecution();
        resolverExecution.setId(88L);
        ConversationTaskAgentExecution editExecution = new ConversationTaskAgentExecution();
        editExecution.setId(89L);
        Mockito.when(conversationTaskAgentExecutionMapper.insertExecution(ArgumentMatchers.any()))
            .thenReturn(resolverExecution, editExecution);
        Mockito.when(conversationTaskAgentExecutionMapper.insertTurnLink(USER_ID, 89L, 77L, 1)).thenReturn(1);
        Mockito.when(fileResourceMapper.getOwnedFileResource("file-source", USER_ID)).thenReturn(sourceResource());
        Mockito.when(conversationRoundGeneratedFileMapper.findLatestVisibleRoundNumber(CONVERSATION_ID, 55L))
            .thenReturn(9L);
        ConversationRound sourceRound = round();
        sourceRound.setId(41L);
        Mockito.when(conversationRoundMapper.getRound(CONVERSATION_ID, 9L)).thenReturn(sourceRound);
        ReferenceResolutionAudit audit = ReferenceResolutionAudit.builder()
            .taskAgentId(4L)
            .taskAgentName("image-reference-resolver")
            .taskAgentVersion(1)
            .status(ResolverExecutionStatus.COMPLETED)
            .resolvedFileId("file-source")
            .resolvedRoundNumber(9L)
            .resolutionReason("most recent image")
            .startTime(START_TIME)
            .endTime(START_TIME.plusSeconds(1))
            .model("ZhipuAI/GLM-5.3-Flash")
            .build();

        service.materialize(withEditContent(
            editRequest(GenerationAttemptStatus.COMPLETED, EditResolutionKind.REFERENCE_RESOLVER, "file-source", audit),
            content));

        ArgumentCaptor<ConversationTaskAgentExecution> executionCaptor =
            ArgumentCaptor.forClass(ConversationTaskAgentExecution.class);
        Mockito.verify(conversationTaskAgentExecutionMapper, Mockito.times(2))
            .insertExecution(executionCaptor.capture());
        Assertions.assertEquals("builtin.resolve_image_reference",
            executionCaptor.getAllValues().get(0).getCapabilityKey());
        Assertions.assertEquals(TaskAgentExecutionStatus.COMPLETED,
            executionCaptor.getAllValues().get(0).getStatus());
        Assertions.assertEquals("builtin.edit_image", executionCaptor.getAllValues().get(1).getCapabilityKey());
        ArgumentCaptor<ConversationRoundEditSource> sourceCaptor =
            ArgumentCaptor.forClass(ConversationRoundEditSource.class);
        Mockito.verify(conversationRoundEditSourceMapper).insertEditSource(sourceCaptor.capture());
        Assertions.assertEquals(88L, sourceCaptor.getValue().getResolverExecutionId());
        Assertions.assertEquals("most recent image", sourceCaptor.getValue().getResolutionReason());
        Assertions.assertEquals(EditResolutionKind.REFERENCE_RESOLVER, sourceCaptor.getValue().getResolutionKind());
    }

    @Test
    void persistsBoundedResolverFailureReasonForUnresolvedEdit() throws FileProcessingException
    {
        arrangeAuthorizedRound();
        ConversationTaskAgentExecution resolverExecution = new ConversationTaskAgentExecution();
        resolverExecution.setId(88L);
        ConversationTaskAgentExecution editExecution = new ConversationTaskAgentExecution();
        editExecution.setId(89L);
        Mockito.when(conversationGenerationAttemptMapper.insertOrGet(ArgumentMatchers.any()))
            .thenAnswer(invocation -> persistedAttempt(invocation.getArgument(0)));
        Mockito.when(conversationTurnMapper.getTurn(42L, SOURCE_TURN_NUMBER)).thenReturn(sourceTurn());
        Mockito.when(conversationTaskAgentExecutionMapper.insertExecution(ArgumentMatchers.any()))
            .thenReturn(resolverExecution, editExecution);
        Mockito.when(conversationTaskAgentExecutionMapper.insertTurnLink(USER_ID, 88L, 77L, 1)).thenReturn(1);
        Mockito.when(conversationTaskAgentExecutionMapper.insertTurnLink(USER_ID, 89L, 77L, 1)).thenReturn(1);
        ReferenceResolutionAudit audit = ReferenceResolutionAudit.builder()
            .taskAgentId(4L)
            .taskAgentName("image-reference-resolver")
            .taskAgentVersion(1)
            .status(ResolverExecutionStatus.FAILED)
            .resolutionReason("No image candidates were found in the recent Rounds.")
            .startTime(START_TIME)
            .endTime(START_TIME.plusSeconds(1))
            .model("ZhipuAI/GLM-5.3-Flash")
            .build();

        service.materialize(withEditContent(
            editRequest(GenerationAttemptStatus.FAILED, null, "", audit), png(2, 2)));

        ArgumentCaptor<ConversationTaskAgentExecution> executionCaptor =
            ArgumentCaptor.forClass(ConversationTaskAgentExecution.class);
        Mockito.verify(conversationTaskAgentExecutionMapper, Mockito.times(2))
            .insertExecution(executionCaptor.capture());
        ConversationTaskAgentExecution persistedResolver = executionCaptor.getAllValues().get(0);
        Assertions.assertEquals("builtin.resolve_image_reference", persistedResolver.getCapabilityKey());
        Assertions.assertEquals(TaskAgentExecutionStatus.FAILED, persistedResolver.getStatus());
        Assertions.assertEquals("No image candidates were found in the recent Rounds.",
            persistedResolver.getErrorMessage());
        Mockito.verify(conversationRoundEditSourceMapper, Mockito.never())
            .insertEditSource(ArgumentMatchers.any());
    }

    private byte[] arrangeCompletedMaterialization() throws FileProcessingException
    {
        arrangeAuthorizedRound();
        byte[] content = png(2, 2);
        SanitizedImage sanitized = sanitized(content, 2, 2);
        Mockito.when(conversationImageSanitizer.sanitize(ArgumentMatchers.any(FileResource.class), Mockito.same(content)))
            .thenReturn(sanitized);
        Mockito.when(conversationGenerationAttemptMapper.insertOrGet(ArgumentMatchers.any()))
            .thenAnswer(invocation -> persistedAttempt(invocation.getArgument(0)));
        Mockito.when(fileResourceMapper.insertGeneratedFileResource(ArgumentMatchers.any())).thenReturn(persistedResource());
        Mockito.when(fileResourceVariantMapper.upsertPending(ArgumentMatchers.eq(99L), ArgumentMatchers.eq(USER_ID),
            ArgumentMatchers.eq(FileVariantType.MODEL_INPUT), ArgumentMatchers.eq("generated-files"),
            ArgumentMatchers.anyString())).thenReturn(1);
        Mockito.when(fileResourceVariantMapper.markReady(ArgumentMatchers.eq(99L), ArgumentMatchers.eq(USER_ID),
            ArgumentMatchers.eq(FileVariantType.MODEL_INPUT), ArgumentMatchers.eq("image/png"),
            ArgumentMatchers.eq((long) content.length), ArgumentMatchers.eq(sanitized.sha256()), ArgumentMatchers.eq(2),
            ArgumentMatchers.eq(2))).thenReturn(1);
        Mockito.when(conversationRoundGeneratedFileMapper.insertGeneratedFile(ArgumentMatchers.any())).thenReturn(1);
        Mockito.when(conversationTurnMapper.getTurn(42L, SOURCE_TURN_NUMBER)).thenReturn(sourceTurn());
        ConversationTaskAgentExecution editExecution = new ConversationTaskAgentExecution();
        editExecution.setId(88L);
        Mockito.when(conversationTaskAgentExecutionMapper.insertExecution(ArgumentMatchers.any()))
            .thenReturn(editExecution);
        Mockito.when(conversationTaskAgentExecutionMapper.insertTurnLink(USER_ID, 88L, 77L, 1)).thenReturn(1);
        Mockito.when(conversationRoundEditSourceMapper.insertEditSource(ArgumentMatchers.any())).thenReturn(1);
        return content;
    }

    private GeneratedFileMaterializationRequest editRequest(GenerationAttemptStatus status,
                                                            EditResolutionKind resolutionKind,
                                                            String sourceFileId,
                                                            ReferenceResolutionAudit audit)
    {
        return GeneratedFileMaterializationRequest.builder()
            .userId(USER_ID)
            .conversationId(CONVERSATION_ID)
            .roundNumber(3L)
            .attemptId("attempt-1")
            .capabilityKey("builtin.edit_image")
            .model("doubao-seedream-4-0-250828")
            .status(status)
            .sourceTurnNumber(SOURCE_TURN_NUMBER)
            .originalFilename("edited.png")
            .mimeType("image/png")
            .content(png(2, 2))
            .sha256("")
            .width(2)
            .height(2)
            .outputKind(GeneratedOutputKind.IMAGE)
            .rewrittenInstruction("make the circle red")
            .providerRequestId("provider-1")
            .errorCode("")
            .errorMessage("")
            .startTime(START_TIME)
            .endTime(START_TIME.plusSeconds(5))
            .requestId("request-1")
            .traceId("trace-1")
            .taskAgentId(3L)
            .taskAgentName("image-editing-agent")
            .taskAgentVersion(1)
            .normalizedSettingsJson("{}")
            .parentSpanId("parent-span")
            .taskSpanId("task-span")
            .editResolutionKind(resolutionKind)
            .editSourceFileId(sourceFileId)
            .editSourceRoundNumber(9L)
            .referenceResolution(audit)
            .build();
    }

    private GeneratedFileMaterializationRequest withEditContent(GeneratedFileMaterializationRequest request,
                                                                byte[] content)
    {
        return GeneratedFileMaterializationRequest.builder()
            .userId(request.userId())
            .conversationId(request.conversationId())
            .roundNumber(request.roundNumber())
            .attemptId(request.attemptId())
            .capabilityKey(request.capabilityKey())
            .model(request.model())
            .status(request.status())
            .sourceTurnNumber(request.sourceTurnNumber())
            .originalFilename(request.originalFilename())
            .mimeType(request.mimeType())
            .content(content)
            .sha256(request.sha256())
            .width(request.width())
            .height(request.height())
            .outputKind(request.outputKind())
            .rewrittenInstruction(request.rewrittenInstruction())
            .providerRequestId(request.providerRequestId())
            .errorCode(request.errorCode())
            .errorMessage(request.errorMessage())
            .startTime(request.startTime())
            .endTime(request.endTime())
            .requestId(request.requestId())
            .traceId(request.traceId())
            .taskAgentId(request.taskAgentId())
            .taskAgentName(request.taskAgentName())
            .taskAgentVersion(request.taskAgentVersion())
            .normalizedSettingsJson(request.normalizedSettingsJson())
            .parentSpanId(request.parentSpanId())
            .taskSpanId(request.taskSpanId())
            .editResolutionKind(request.editResolutionKind())
            .editSourceFileId(request.editSourceFileId())
            .editSourceRoundNumber(request.editSourceRoundNumber())
            .referenceResolution(request.referenceResolution())
            .build();
    }

    private FileResource sourceResource()
    {
        FileResource resource = new FileResource();
        resource.setId(55L);
        resource.setCreatorId(USER_ID);
        resource.setFileId("file-source");
        resource.setKind(ConversationFileKind.IMAGE);
        resource.setStatus(ConversationFileStatus.READY);
        resource.setOrigin(FileResourceOrigin.GENERATED);
        resource.setOriginalFilename("generated.png");
        resource.setDetectedMimeType("image/png");
        resource.setFileSize(2_048L);
        resource.setWidth(2);
        resource.setHeight(2);
        resource.setSha256("a".repeat(64));
        return resource;
    }

    private GeneratedFileMaterializationRequest request(GenerationAttemptStatus status)
    {
        return GeneratedFileMaterializationRequest.builder()
            .userId(USER_ID)
            .conversationId(CONVERSATION_ID)
            .roundNumber(3L)
            .attemptId("attempt-1")
            .capabilityKey("builtin.generate_image")
            .model("doubao-seedream-4-0-250828")
            .status(status)
            .sourceTurnNumber(SOURCE_TURN_NUMBER)
            .originalFilename("generated.png")
            .mimeType("image/png")
            .content(png(2, 2))
            .sha256("")
            .width(2)
            .height(2)
            .outputKind(GeneratedOutputKind.IMAGE)
            .rewrittenInstruction("draw a blue cat")
            .providerRequestId("provider-1")
            .errorCode("")
            .errorMessage("")
            .startTime(START_TIME)
            .endTime(START_TIME.plusSeconds(5))
            .requestId("request-1")
            .traceId("trace-1")
            .taskAgentId(0L)
            .taskAgentName("")
            .taskAgentVersion(0)
            .normalizedSettingsJson("{}")
            .parentSpanId("")
            .taskSpanId("")
            .build();
    }

    private GeneratedFileMaterializationRequest taskRequest(GenerationAttemptStatus status)
    {
        return GeneratedFileMaterializationRequest.builder()
            .userId(USER_ID)
            .conversationId(CONVERSATION_ID)
            .roundNumber(3L)
            .attemptId("attempt-1")
            .capabilityKey("builtin.generate_image")
            .model("doubao-seedream-4-0-250828")
            .status(status)
            .sourceTurnNumber(SOURCE_TURN_NUMBER)
            .originalFilename("generated.png")
            .mimeType("image/png")
            .content(png(2, 2))
            .sha256("")
            .width(2)
            .height(2)
            .outputKind(GeneratedOutputKind.IMAGE)
            .rewrittenInstruction("draw a blue cat")
            .providerRequestId("provider-1")
            .errorCode("")
            .errorMessage("")
            .startTime(START_TIME)
            .endTime(START_TIME.plusSeconds(5))
            .requestId("request-1")
            .traceId("trace-1")
            .taskAgentId(2L)
            .taskAgentName("image-generation-agent")
            .taskAgentVersion(1)
            .normalizedSettingsJson("{}")
            .parentSpanId("parent-span")
            .taskSpanId("task-span")
            .build();
    }

    private GeneratedFileMaterializationRequest withContent(
        GeneratedFileMaterializationRequest request, byte[] content)
    {
        return GeneratedFileMaterializationRequest.builder()
            .userId(request.userId())
            .conversationId(request.conversationId())
            .roundNumber(request.roundNumber())
            .attemptId(request.attemptId())
            .capabilityKey(request.capabilityKey())
            .model(request.model())
            .status(request.status())
            .sourceTurnNumber(request.sourceTurnNumber())
            .originalFilename(request.originalFilename())
            .mimeType(request.mimeType())
            .content(content)
            .sha256(request.sha256())
            .width(request.width())
            .height(request.height())
            .outputKind(request.outputKind())
            .rewrittenInstruction(request.rewrittenInstruction())
            .providerRequestId(request.providerRequestId())
            .errorCode(request.errorCode())
            .errorMessage(request.errorMessage())
            .startTime(request.startTime())
            .endTime(request.endTime())
            .requestId(request.requestId())
            .traceId(request.traceId())
            .taskAgentId(request.taskAgentId())
            .taskAgentName(request.taskAgentName())
            .taskAgentVersion(request.taskAgentVersion())
            .normalizedSettingsJson(request.normalizedSettingsJson())
            .parentSpanId(request.parentSpanId())
            .taskSpanId(request.taskSpanId())
            .build();
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
