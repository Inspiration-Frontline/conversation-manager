package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationLlmRequestMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** MyBatis persistence operations for normalized model-request messages. */
@Mapper
public interface ConversationLlmRequestMessageMapper
{
    /**
     * Inserts an ordered batch and returns rows with generated database identities.
     *
     * @param messages normalized messages across already-persisted Turns
     * @return inserted messages carrying generated IDs
     */
    List<ConversationLlmRequestMessage> insertRequestMessages(
        @Param("items") List<ConversationLlmRequestMessage> messages);

    /**
     * Loads the source-of-truth request messages for one Turn.
     *
     * @param turnId database identity of the containing Turn
     * @return messages ordered by {@code message_order}
     */
    List<ConversationLlmRequestMessage> listRequestMessages(@Param("turnId") long turnId);

    /**
     * Loads every normalized request message needed to inspect or replay one Round.
     *
     * @param roundId database identity of the containing Round
     * @return messages ordered by Turn and message position
     */
    List<ConversationLlmRequestMessage> listRequestMessagesForRound(@Param("roundId") long roundId);
}
