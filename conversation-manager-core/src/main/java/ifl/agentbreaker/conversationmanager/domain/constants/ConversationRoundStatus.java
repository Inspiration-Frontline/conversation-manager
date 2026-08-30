package ifl.agentbreaker.conversationmanager.domain.constants;

/** Durable lifecycle states for one user-request Round. */
public enum ConversationRoundStatus
{
    /** Final answer and all normalized execution evidence committed successfully. */
    COMPLETED,
    /** Execution terminated with a durable failure reason. */
    FAILED,
    /** The user or transport cancelled execution before normal completion. */
    CANCELLED,
    /** Incremental execution is active and may accept append/finalize mutations. */
    IN_PROGRESS,
}
