package ifl.agentbreaker.conversationmanager.domain.dtos.responses;

import ifl.agentbreaker.conversationmanager.domain.constants.ConversationFileKind;
import ifl.agentbreaker.conversationmanager.domain.constants.ConversationFileStatus;
import lombok.Data;

import java.time.Instant;

/** User-visible file metadata returned by Conversation Manager APIs. */
@Data
public class FileResourceInfo
{
    /** Stable identifier of the file. */
    private String fileId;
    /** Original filename retained for display. */
    private String originalFilename;
    /** Classified file kind used to select parsing and model handling. */
    private ConversationFileKind kind;
    /** Current lifecycle or execution status. */
    private ConversationFileStatus status;
    /** Monotonic revision incremented when the lifecycle status changes. */
    private long statusRevision;
    /** MIME type detected from uploaded bytes. */
    private String mimeType;
    /** Uploaded object size in bytes. */
    private long fileSize;
    /** SHA-256 digest of the immutable uploaded object. */
    private String sha256;
    /** Image width when the resource is an image. */
    private Integer width;
    /** Image height when the resource is an image. */
    private Integer height;
    /** Stable processing error code, when processing failed. */
    private String errorCode;
    /** Client-safe processing error message, when processing failed. */
    private String errorMessage;
    /** Whether extracted text was bounded before entering model context. */
    private boolean extractionTruncated;
    /** UTC instant when the record was created. */
    private Instant creationTime;
    /** UTC instant when the record was last modified. */
    private Instant modificationTime;
}
