package ifl.agentbreaker.conversationmanager.domain.dtos.responses;

public record ResolvedConversationReference(
    String sourceConversationId,
    String sourceTitle,
    long sourceEndRoundNumber)
{
}
