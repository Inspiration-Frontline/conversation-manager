package ifl.agentbreaker.conversationmanager.domain.constants;

public enum ConversationFileStatus
{
    PENDING_UPLOAD,
    VALIDATING,
    PROCESSING,
    READY,
    FAILED,
    CANCELLED,
    DELETE_REQUESTED,
    DELETED,
    EXPIRED,
}
