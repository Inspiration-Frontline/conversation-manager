package ifl.agentbreaker.conversationmanager.domain.dtos.responses;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationRound;

import java.util.List;

/**
 * Owner-scoped Round summaries and the durable Conversation high-water mark.
 *
 * @param latestRoundNumber Highest allocated Round number, including partial terminal Rounds
 * @param rounds Ordered Round projections visible in history
 */
public record ConversationRoundHistoryResult(long latestRoundNumber, List<ConversationRound> rounds)
{
}
