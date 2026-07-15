package ifl.agentbreaker.conversationmanager.domain.dtos.responses;

import ifl.agentbreaker.conversationmanager.rpc.LlmConversationMessage;

import java.util.List;

public record ConversationReplayResult(String conversationId, List<LlmConversationMessage> contextMessages)
{
}
