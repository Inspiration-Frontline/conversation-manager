package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.dtos.responses.RoundFileHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/** MyBatis persistence operations for immutable Round-to-file references. */
@Mapper
public interface ConversationRoundFileMapper
{
    /**
     * Links an ordered file selection to a Round after owner authorization.
     *
     * @param roundId database identity of the destination Round
     * @param userId authenticated owner used to validate every file resource
     * @param fileResourceIds database identities in request display order
     * @return affected row count
     */
    int insertRoundFiles(@Param("roundId") long roundId,
                         @Param("userId") long userId,
                         @Param("fileResourceIds") Collection<Long> fileResourceIds);

    /**
     * Loads attachment summaries for every active Round in one Conversation.
     *
     * @param conversationId stable Conversation identifier
     * @return attachment summaries ordered by Round and file position
     */
    List<RoundFileHistory> listRoundFiles(@Param("conversationId") String conversationId);

    /**
     * Loads attachments belonging to completed Rounds inside an inclusive snapshot boundary.
     *
     * @param conversationId stable source Conversation identifier
     * @param endRoundNumber inclusive shared or forked boundary
     * @return attachment summaries ordered by Round and file position
     */
    List<RoundFileHistory> listCompletedRoundFilesAtOrBefore(@Param("conversationId") String conversationId,
                                                              @Param("endRoundNumber") long endRoundNumber);

    /**
     * Resolves one file only when it belongs to a completed Round within a shared boundary.
     *
     * @param conversationId stable source Conversation identifier
     * @param endRoundNumber inclusive frozen share boundary
     * @param fileId stable file resource identifier
     * @return authorized attachment summary, or {@code null} when outside the snapshot
     */
    RoundFileHistory getSharedRoundFile(@Param("conversationId") String conversationId,
                                        @Param("endRoundNumber") long endRoundNumber,
                                        @Param("fileId") String fileId);

    /**
     * Deletes links before the owning Conversations are logically deleted.
     *
     * @param conversationIds stable Conversation identifiers being deleted
     * @param userId authenticated owner recorded by the cleanup operation
     * @return affected row count
     */
    int deleteByConversationIds(@Param("conversationIds") Collection<String> conversationIds,
                                @Param("userId") long userId);
}
