package ifl.agentbreaker.conversationmanager.domain.dtos;

/**
 * Immutable source Conversation and inclusive Round boundary used by batched reference queries.
 *
 * @param sourceConversationId stable source Conversation ID
 * @param sourceEndRoundNumber inclusive frozen Round boundary
 */
public record ConversationReferenceBoundary(String sourceConversationId, long sourceEndRoundNumber)
{
}
