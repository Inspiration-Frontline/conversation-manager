package ifl.agentbreaker.conversationmanager.domain.dtos.responses;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationRound;

import java.util.List;

public record ConversationRoundHistoryResult(long latestRoundNumber, List<ConversationRound> rounds)
{
}
