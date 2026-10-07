package ifl.agentbreaker.conversationmanager.services.files;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import ifl.agentbreaker.conversationmanager.config.OssStorageProperties;
import ifl.agentbreaker.conversationmanager.dao.ConversationRoundInputFileMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationRoundGeneratedFileMapper;
import ifl.agentbreaker.conversationmanager.dao.FileResourceMapper;
import ifl.agentbreaker.conversationmanager.dao.FileResourceVariantMapper;
import ifl.agentbreaker.conversationmanager.domain.constants.ConversationFileKind;
import ifl.agentbreaker.conversationmanager.domain.constants.ConversationFileStatus;
import ifl.agentbreaker.conversationmanager.domain.constants.FileResourceOrigin;
import ifl.agentbreaker.conversationmanager.domain.constants.FileVariantType;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.PreparedImageEditSource;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.FileResource;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.FileResourceVariant;
import ifl.agentbreaker.conversationmanager.exceptions.ServiceResponseException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URL;

@ExtendWith(MockitoExtension.class)
class ConversationFileEditSourceTest
{
    /** Owner identity used by every authorized fixture. */
    private static final long USER_ID = 7L;

    /** Stable Conversation identity used by the source fixtures. */
    private static final String CONVERSATION_ID = "conversation-edit";

    /** Owned-resource lookup mock. */
    @Mock
    private FileResourceMapper fileResourceMapper;

    /** Uploaded-source visibility lookup mock. */
    @Mock
    private ConversationRoundInputFileMapper conversationRoundFileMapper;

    /** Generated-source visibility lookup mock. */
    @Mock
    private ConversationRoundGeneratedFileMapper conversationRoundGeneratedFileMapper;

    /** Sanitized derivative lookup mock. */
    @Mock
    private FileResourceVariantMapper fileResourceVariantMapper;

    /** Storage configuration mock. */
    @Mock
    private OssStorageProperties ossStorageProperties;

    /** Private OSS client mock used by signed URL creation. */
    @Mock
    private OSS oss;

    /** Service under test. */
    @InjectMocks
    private ConversationFileService service;

    @Test
    void resolvesUploadedSourceThroughTheSanitizedVariant() throws Exception
    {
        Mockito.when(fileResourceMapper.getOwnedFileResource("file-upload", USER_ID))
            .thenReturn(imageResource("file-upload", FileResourceOrigin.USER_UPLOAD));
        Mockito.when(conversationRoundFileMapper.findLatestVisibleRoundNumber(CONVERSATION_ID, 55L)).thenReturn(4L);
        FileResourceVariant variant = new FileResourceVariant();
        variant.setBucketName("bucket");
        variant.setObjectKey("user/1/2026/10/file-upload/model-input");
        variant.setMimeType("image/png");
        variant.setFileSize(120L);
        variant.setSha256("b".repeat(64));
        variant.setWidth(64);
        variant.setHeight(48);
        Mockito.when(fileResourceVariantMapper.getReadyVariant(55L, FileVariantType.MODEL_INPUT)).thenReturn(variant);
        Mockito.when(ossStorageProperties.getPresignedUrlTtlSeconds()).thenReturn(300);
        Mockito.when(oss.generatePresignedUrl(ArgumentMatchers.any(GeneratePresignedUrlRequest.class)))
            .thenReturn(new URL("https://oss.example/signed"));

        PreparedImageEditSource prepared = service.prepareImageEditSource(USER_ID, CONVERSATION_ID, "file-upload");

        Assertions.assertEquals("file-upload", prepared.fileId());
        Assertions.assertEquals("image/png", prepared.mimeType());
        Assertions.assertEquals(120L, prepared.fileSize());
        Assertions.assertEquals(64, prepared.width());
        Assertions.assertEquals(48, prepared.height());
        Assertions.assertEquals("USER_UPLOAD", prepared.origin());
        Assertions.assertEquals(4L, prepared.sourceRoundNumber());
        Assertions.assertEquals("https://oss.example/signed", prepared.downloadUrl());
        Mockito.verify(conversationRoundGeneratedFileMapper, Mockito.never())
            .findLatestVisibleRoundNumber(ArgumentMatchers.anyString(), ArgumentMatchers.anyLong());
    }

    @Test
    void resolvesGeneratedSourceThroughTheOriginalObject() throws Exception
    {
        Mockito.when(fileResourceMapper.getOwnedFileResource("file-generated", USER_ID))
            .thenReturn(imageResource("file-generated", FileResourceOrigin.GENERATED));
        Mockito.when(conversationRoundGeneratedFileMapper.findLatestVisibleRoundNumber(CONVERSATION_ID, 55L))
            .thenReturn(9L);
        Mockito.when(ossStorageProperties.getPresignedUrlTtlSeconds()).thenReturn(300);
        Mockito.when(oss.generatePresignedUrl(ArgumentMatchers.any(GeneratePresignedUrlRequest.class)))
            .thenReturn(new URL("https://oss.example/original"));

        PreparedImageEditSource prepared = service.prepareImageEditSource(USER_ID, CONVERSATION_ID, "file-generated");

        Assertions.assertEquals("https://oss.example/original", prepared.downloadUrl());
        Assertions.assertEquals("GENERATED", prepared.origin());
        Assertions.assertEquals(9L, prepared.sourceRoundNumber());
        Mockito.verifyNoInteractions(fileResourceVariantMapper);
        Mockito.verify(conversationRoundFileMapper, Mockito.never())
            .findLatestVisibleRoundNumber(ArgumentMatchers.anyString(), ArgumentMatchers.anyLong());
    }

    @Test
    void rejectsSourceThatIsNotVisibleInTheConversation()
    {
        Mockito.when(fileResourceMapper.getOwnedFileResource("file-upload", USER_ID))
            .thenReturn(imageResource("file-upload", FileResourceOrigin.USER_UPLOAD));
        Mockito.when(conversationRoundFileMapper.findLatestVisibleRoundNumber(CONVERSATION_ID, 55L)).thenReturn(null);

        Assertions.assertThrows(ServiceResponseException.class,
            () -> service.prepareImageEditSource(USER_ID, CONVERSATION_ID, "file-upload"));
        Mockito.verifyNoInteractions(oss);
    }

    @Test
    void rejectsSourceThatIsNotAReadyImage()
    {
        FileResource resource = imageResource("file-upload", FileResourceOrigin.USER_UPLOAD);
        resource.setStatus(ConversationFileStatus.PROCESSING);
        Mockito.when(fileResourceMapper.getOwnedFileResource("file-upload", USER_ID)).thenReturn(resource);

        Assertions.assertThrows(ServiceResponseException.class,
            () -> service.prepareImageEditSource(USER_ID, CONVERSATION_ID, "file-upload"));
        Mockito.verifyNoInteractions(oss, conversationRoundFileMapper, conversationRoundGeneratedFileMapper);
    }

    private FileResource imageResource(String fileId, FileResourceOrigin origin)
    {
        FileResource resource = new FileResource();
        resource.setId(55L);
        resource.setCreatorId(USER_ID);
        resource.setFileId(fileId);
        resource.setKind(ConversationFileKind.IMAGE);
        resource.setStatus(ConversationFileStatus.READY);
        resource.setOrigin(origin);
        resource.setOriginalFilename("source.png");
        resource.setDetectedMimeType("image/png");
        resource.setDeclaredMimeType("image/png");
        resource.setFileSize(2_048L);
        resource.setWidth(320);
        resource.setHeight(240);
        resource.setSha256("a".repeat(64));
        resource.setBucketName("bucket");
        resource.setObjectKey("key");
        return resource;
    }
}
