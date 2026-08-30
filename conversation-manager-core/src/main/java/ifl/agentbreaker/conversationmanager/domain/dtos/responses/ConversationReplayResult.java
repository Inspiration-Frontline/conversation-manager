package ifl.agentbreaker.conversationmanager.domain.dtos.responses;

import ifl.agentbreaker.conversationmanager.rpc.LlmConversationMessage;

import java.util.List;

/**
 * Provider-neutral replay context ending at one immutable Round boundary.
 *
 * @param conversationId Conversation whose context was reconstructed
 * @param contextMessages Ordered messages supplied to the next model request
 */
public record ConversationReplayResult(String conversationId, List<LlmConversationMessage> contextMessages)
{
}
