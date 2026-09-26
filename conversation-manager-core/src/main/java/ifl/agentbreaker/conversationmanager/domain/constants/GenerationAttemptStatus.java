package ifl.agentbreaker.conversationmanager.domain.constants;

/** Durable lifecycle states for one provider generation attempt. */
public enum GenerationAttemptStatus
{
    READY,
    DISPATCHING,
    COMPLETED,
    FAILED,
    CANCELLED,
    UNKNOWN,
    MATERIALIZED
}
