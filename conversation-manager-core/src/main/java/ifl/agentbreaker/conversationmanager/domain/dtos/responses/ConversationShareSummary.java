package ifl.agentbreaker.conversationmanager.domain.dtos.responses;

import java.time.Instant;
import java.util.Date;

/** Owner-visible metadata for one independent share link. */
public record ConversationShareSummary(
    String parentConversationId,
    String sharedConversationId,
    String title,
    Date creationTime,
    long endRoundNumber,
    Instant expiresAt,
    boolean revoked,
    Instant revokedAt)
{
}
