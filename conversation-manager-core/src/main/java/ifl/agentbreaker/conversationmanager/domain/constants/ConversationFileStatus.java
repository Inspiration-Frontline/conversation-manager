package ifl.agentbreaker.conversationmanager.domain.constants;

/** Durable lifecycle states for an uploaded Conversation file. */
public enum ConversationFileStatus
{
    /** Upload session exists but object bytes are not yet confirmed. */
    PENDING_UPLOAD,
    /** Uploaded bytes are undergoing integrity and content checks. */
    VALIDATING,
    /** A leased background task is extracting file content. */
    PROCESSING,
    /** Extraction completed and the file may be attached to a Round. */
    READY,
    /** Validation or extraction failed and may be retried when eligible. */
    FAILED,
    /** Processing was cancelled before a usable result was produced. */
    CANCELLED,
    /** Logical deletion is committed and physical object cleanup is queued. */
    DELETE_REQUESTED,
    /** Physical object cleanup completed. */
    DELETED,
    /** An unconfirmed upload session passed its retention deadline. */
    EXPIRED,
}
