package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import ifl.agentbreaker.conversationmanager.domain.constants.ConversationFileKind;
import ifl.agentbreaker.conversationmanager.domain.constants.ConversationFileStatus;
import ifl.agentbreaker.conversationmanager.domain.valueobjects.FileExtractionMetadata;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

/** Durable metadata and lifecycle state for one uploaded file object. */
@Data
@EqualsAndHashCode(callSuper = true)
public class FileResource extends EntityBase
{
    /** Stable identifier of the file. */
    private String fileId;
    /** Classified file kind selecting parser and model handling. */
    private ConversationFileKind kind;
    /** Current lifecycle or execution status. */
    private ConversationFileStatus status;
    /**
     * Monotonic version of the user-visible file status. SQL increments it only when the status
     * transitions, allowing polling clients to detect a newer state without comparing timestamps.
     */
    private long statusRevision;
    /** Private OSS bucket containing the immutable object. */
    private String bucketName;
    /** Server-generated OSS object key; untrusted filenames never become keys. */
    private String objectKey;
    /** Original client filename retained for display and detection hints. */
    private String originalFilename;
    /** Normalized lowercase extension used to select parsing rules. */
    private String fileExtension;
    /** Client-declared MIME type retained as an upload hint. */
    private String declaredMimeType;
    /** MIME type detected from immutable object bytes. */
    private String detectedMimeType;
    /** Expected or confirmed object size in bytes. */
    private long fileSize;
    /** SHA-256 digest verified against the immutable object. */
    private String sha256;
    /** Bounded text extracted for model context, when applicable. */
    private String extractedText;
    /** Typed JSONB describing format-specific extraction evidence; see FileExtractionMetadata. */
    private FileExtractionMetadata extractionMetadata;
    /** Whether retained extraction omits content due to configured bounds. */
    private boolean extractionTruncated;
    /** Image width in pixels, when applicable. */
    private Integer width;
    /** Image height in pixels, when applicable. */
    private Integer height;
    /** Stable processing error classification, when failed. */
    private String errorCode;
    /** Client-safe processing failure explanation, when failed. */
    private String errorMessage;
    /** UTC instant marking upload expires at. */
    private Instant uploadExpiresAt;
    /** UTC instant marking confirmed time. */
    private Instant confirmedTime;
    /** UTC instant marking ready time. */
    private Instant readyTime;
    /** UTC instant marking orphaned time. */
    private Instant orphanedTime;
    /** Stable identifier of the reserved conversation. */
    private String reservedConversationId;
    /** Stable identifier of the reserved request. */
    private String reservedRequestId;
    /** Expiry of the temporary reservation held by a Round request. */
    private Instant reservedUntil;
    /** Whether the record is logically deleted. */
    private boolean deleted;
}
