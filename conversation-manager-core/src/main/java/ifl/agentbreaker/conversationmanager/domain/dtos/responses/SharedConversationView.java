package ifl.agentbreaker.conversationmanager.domain.dtos.responses;

import java.time.Instant;

/** Authenticated read-only snapshot returned for a valid share link. */
public record SharedConversationView(
    String sharedConversationId,
    String title,
    Instant expiresAt,
    SharedRoundHistoryView history)
{
}
