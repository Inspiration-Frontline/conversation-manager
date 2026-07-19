package ifl.agentbreaker.conversationmanager.domain.constants;

/**
 * Execution lifecycle shared by the persisted file-processing and file-cleanup background jobs.
 * This is a task state, not the user-visible lifecycle of {@code FileResource}.
 */
public enum FileTaskExecutionStatus
{
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
}
