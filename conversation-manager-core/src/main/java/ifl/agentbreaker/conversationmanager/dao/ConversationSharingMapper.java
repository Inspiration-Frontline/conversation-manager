package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationSharing;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.ConversationShareSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/** MyBatis persistence operations for authenticated Conversation share snapshots. */
@Mapper
public interface ConversationSharingMapper
{
    /**
     * Inserts one immutable share boundary.
     *
     * @param sharing initialized share token and snapshot metadata
     * @return affected row count
     */
    int insertConversationSharing(ConversationSharing sharing);

    /**
     * Loads a share regardless of expiry or revocation for owner management.
     *
     * @param sharedConversationId stable share identifier
     * @return share row, or {@code null} when absent
     */
    ConversationSharing getConversationSharingBySharedId(@Param("sharedConversationId") String sharedConversationId);

    /**
     * Resolves only a currently active, unexpired share.
     *
     * @param sharedConversationId stable share identifier
     * @return active share row, or {@code null} when inaccessible
     */
    ConversationSharing getActiveConversationSharingBySharedId(@Param("sharedConversationId") String sharedConversationId);

    /**
     * Lists shares created for one owned source Conversation.
     *
     * @param parentConversationId stable source Conversation identifier
     * @param userId authenticated owner ID
     * @return shares ordered by creation time
     */
    List<ConversationSharing> listConversationSharingsByParentId(@Param("parentConversationId") String parentConversationId,
                                                                  @Param("userId") long userId);

    /**
     * Lists owner-management summaries across all source Conversations.
     *
     * @param userId authenticated owner ID
     * @return share summaries with source titles and active state
     */
    List<ConversationShareSummary> listAllConversationShareSummaries(@Param("userId") long userId);

    /**
     * Revokes one share after owner validation.
     *
     * @param sharedConversationId stable share identifier
     * @param userId authenticated source owner ID
     * @return affected row count
     */
    int revokeConversationSharing(@Param("sharedConversationId") String sharedConversationId,
                                  @Param("userId") long userId);

    /**
     * Revokes every share for Conversations being logically deleted.
     *
     * @param conversationIds stable source Conversation identifiers
     * @param userId authenticated owner ID
     * @return affected row count
     */
    int revokeByParentConversationIds(@Param("conversationIds") Collection<String> conversationIds,
                                      @Param("userId") long userId);
}
