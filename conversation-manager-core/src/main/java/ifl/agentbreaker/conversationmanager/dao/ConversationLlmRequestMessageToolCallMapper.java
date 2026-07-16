package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationLlmRequestMessageToolCall;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ConversationLlmRequestMessageToolCallMapper
{
    ConversationLlmRequestMessageToolCall insertRequestMessageToolCall(
        ConversationLlmRequestMessageToolCall toolCall);

    List<ConversationLlmRequestMessageToolCall> listRequestMessageToolCallsForRound(
        @Param("roundId") long roundId);
}
