package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import ifl.agentbreaker.conversationmanager.domain.constants.ConversationFileKind;
import ifl.agentbreaker.conversationmanager.domain.constants.ConversationFileStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = true)
public class FileResource extends EntityBase
{
    private String fileId;
    private ConversationFileKind kind;
    private ConversationFileStatus status;
    private long statusRevision;
    private String bucketName;
    private String objectKey;
    private String originalFilename;
    private String fileExtension;
    private String declaredMimeType;
    private String detectedMimeType;
    private long fileSize;
    private String sha256;
    private String extractedText;
    private String extractionMetadata;
    private boolean extractionTruncated;
    private Integer width;
    private Integer height;
    private String errorCode;
    private String errorMessage;
    private Date uploadExpiresAt;
    private Date confirmedTime;
    private Date readyTime;
    private Date orphanedTime;
    private String reservedConversationId;
    private String reservedRequestId;
    private Date reservedUntil;
    private boolean deleted;
}
