package ifl.agentbreaker.conversationmanager.domain.constants;

/** Lifecycle state of one deterministic file derivative. */
public enum FileVariantStatus
{
    /** The derivative row exists but its verified object has not been published. */
    PENDING,
    /** The object was encoded, uploaded, decoded again, and verified. */
    READY
}
