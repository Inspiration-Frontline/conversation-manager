package ifl.agentbreaker.conversationmanager.domain.constants;

/**
 * Terminal state of one image-reference-resolver Task-Agent execution.
 */
public enum ResolverExecutionStatus
{
    /**
     * Resolver selected one candidate source image.
     */
    COMPLETED,

    /**
     * Resolver could not select a candidate.
     */
    FAILED,

    /**
     * Resolver was cancelled before producing a result.
     */
    CANCELLED
}
