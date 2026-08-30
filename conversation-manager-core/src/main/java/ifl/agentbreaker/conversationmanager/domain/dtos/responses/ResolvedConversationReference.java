package ifl.agentbreaker.conversationmanager.domain.dtos.responses;

/**
 * Authorized source Conversation boundary prepared for destination execution.
 *
 * @param sourceConversationId Source Conversation identity
 * @param sourceTitle Frozen source title copied into the destination request
 * @param sourceEndRoundNumber Immutable completed Round boundary
 */
public record ResolvedConversationReference(
    String sourceConversationId,
    String sourceTitle,
    long sourceEndRoundNumber)
{
}
