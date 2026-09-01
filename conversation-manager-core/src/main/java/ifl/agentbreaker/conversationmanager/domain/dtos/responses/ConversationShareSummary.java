package ifl.agentbreaker.conversationmanager.domain.dtos.responses;

import java.time.Instant;

/** Owner-visible metadata for one independent share link.
 * @param parentConversationId owned source Conversation identity
 * @param sharedConversationId stable public share identity
 * @param sourceDeleted whether the source Conversation has been logically deleted
 * @param title frozen source title displayed for the share
 * @param creationTime UTC instant when the share was created
 * @param endRoundNumber immutable completed Round boundary exposed by the share
 * @param expiresAt UTC expiry instant, or {@code null} for a non-expiring share
 * @param revoked whether the owner has revoked the share
 * @param revokedAt UTC revocation instant, or {@code null} while active
 */
public record ConversationShareSummary(
    String parentConversationId,
    String sharedConversationId,
    boolean sourceDeleted,
    String title,
    Instant creationTime,
    long endRoundNumber,
    Instant expiresAt,
    boolean revoked,
    Instant revokedAt)
{
}
