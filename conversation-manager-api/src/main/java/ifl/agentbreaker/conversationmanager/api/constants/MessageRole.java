package ifl.agentbreaker.conversationmanager.api.constants;

/** Provider-neutral roles supported by the legacy Conversation DTO contract. */
public enum MessageRole
{
    /** Instruction supplied by the system boundary. */
    SYSTEM,
    /** Input supplied by the end user. */
    USER,
    /** Model-authored response or Tool-call message. */
    ASSISTANT,
    /** Result corresponding to a preceding Assistant Tool call. */
    TOOL,
}
