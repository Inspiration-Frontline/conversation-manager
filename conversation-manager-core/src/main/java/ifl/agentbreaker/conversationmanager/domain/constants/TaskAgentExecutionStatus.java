package ifl.agentbreaker.conversationmanager.domain.constants;

/** Durable lifecycle states for one bounded Task-Agent execution. */
public enum TaskAgentExecutionStatus
{
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
    UNKNOWN
}
