package ifl.agentbreaker.conversationmanager.domain.dtos.responses;

import java.time.Instant;

/** Authenticated read-only snapshot returned for a valid share link.
 * @param sharedConversationId stable public share identity
 * @param title frozen source title displayed for the share
 * @param expiresAt UTC expiry instant, or {@code null} for a non-expiring share
 * @param history redacted Round history bounded by the share snapshot
 */
public record SharedConversationView(
    String sharedConversationId,
    String title,
    Instant expiresAt,
    SharedRoundHistoryView history)
{
}
