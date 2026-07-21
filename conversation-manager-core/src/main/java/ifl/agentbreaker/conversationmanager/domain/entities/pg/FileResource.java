package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import ifl.agentbreaker.conversationmanager.domain.constants.ConversationFileKind;
import ifl.agentbreaker.conversationmanager.domain.constants.ConversationFileStatus;
import ifl.agentbreaker.conversationmanager.domain.valueobjects.FileExtractionMetadata;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

@Data
@EqualsAndHashCode(callSuper = true)
public class FileResource extends EntityBase
{
    private String fileId;
    private ConversationFileKind kind;
    private ConversationFileStatus status;
    /**
     * Monotonic version of the user-visible file status. SQL increments it only when the status
     * transitions, allowing polling clients to detect a newer state without comparing timestamps.
     */
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
    /** Typed JSONB describing format-specific extraction evidence; see FileExtractionMetadata. */
    private FileExtractionMetadata extractionMetadata;
    private boolean extractionTruncated;
    private Integer width;
    private Integer height;
    private String errorCode;
    private String errorMessage;
    private Instant uploadExpiresAt;
    private Instant confirmedTime;
    private Instant readyTime;
    private Instant orphanedTime;
    private String reservedConversationId;
    private String reservedRequestId;
    private Instant reservedUntil;
    private boolean deleted;
}
