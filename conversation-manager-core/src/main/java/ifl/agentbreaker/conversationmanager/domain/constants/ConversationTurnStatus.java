package ifl.agentbreaker.conversationmanager.domain.constants;

/** Terminal aggregate states for one model invocation and its triggered Tool executions. */
public enum ConversationTurnStatus
{
    /** The model invocation and every triggered Tool execution reached a terminal outcome. */
    COMPLETED,
    /** The Turn stopped because model or orchestration processing failed. */
    FAILED,
    /** The Turn stopped in response to user or transport cancellation. */
    CANCELLED,
}
