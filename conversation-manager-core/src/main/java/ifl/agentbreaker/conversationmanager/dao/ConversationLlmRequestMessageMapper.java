package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationLlmRequestMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ConversationLlmRequestMessageMapper
{
    List<ConversationLlmRequestMessage> insertRequestMessages(
        @Param("items") List<ConversationLlmRequestMessage> messages);

    List<ConversationLlmRequestMessage> listRequestMessages(@Param("llmCallId") long llmCallId);

    List<ConversationLlmRequestMessage> listRequestMessagesForRound(@Param("roundId") long roundId);
}
