package ifl.agentbreaker.conversationmanager.domain.dtos.responses;

import java.time.Instant;

/** Owner-visible metadata for one independent share link. */
public record ConversationShareSummary(
    String parentConversationId,
    String sharedConversationId,
    String title,
    Instant creationTime,
    long endRoundNumber,
    Instant expiresAt,
    boolean revoked,
    Instant revokedAt)
{
}
