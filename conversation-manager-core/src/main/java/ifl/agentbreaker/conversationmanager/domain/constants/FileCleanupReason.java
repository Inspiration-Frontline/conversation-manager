package ifl.agentbreaker.conversationmanager.domain.constants;

/** Reason recorded when a file resource is queued for physical cleanup. */
public enum FileCleanupReason
{
    /** Upload reservation expired before confirmation. */
    UPLOAD_EXPIRED,
    /** The owning user explicitly removed the file. */
    USER_REMOVED,
    /** No durable Conversation or Round reference remains. */
    ORPHANED,
    /** The Conversation owning the reference was deleted. */
    CONVERSATION_DELETED,
}
