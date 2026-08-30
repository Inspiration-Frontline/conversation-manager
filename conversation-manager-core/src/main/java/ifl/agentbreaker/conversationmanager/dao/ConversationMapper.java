package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.Conversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/** MyBatis persistence operations for Conversation roots, ownership, navigation, and high-water state. */
@Mapper
public interface ConversationMapper
{
    /**
     * Inserts a new Conversation root and returns its generated audit fields.
     *
     * @param conversation initialized owner-scoped Conversation
     * @return inserted Conversation carrying generated database identity and timestamps
     */
    Conversation insertConversation(Conversation conversation);

    /**
     * Loads a Conversation without an owner predicate for trusted internal workflows.
     *
     * @param conversationId stable Conversation identifier
     * @return active Conversation, or {@code null} when absent
     */
    Conversation getConversationById(@Param("conversationId") String conversationId);

    /**
     * Loads one active Conversation owned by the authenticated user.
     *
     * @param conversationId stable Conversation identifier
     * @param userId authenticated owner ID
     * @return owned Conversation, or {@code null} when absent
     */
    Conversation getConversationByIdAndUser(@Param("conversationId") String conversationId, @Param("userId") long userId);

    /**
     * Reads the monotonic Round-number high-water mark for an owned Conversation.
     *
     * @param conversationId stable Conversation identifier
     * @param userId authenticated owner ID
     * @return current high-water mark, or {@code null} when the Conversation is missing
     */
    Long getLatestRoundNumberByIdAndUser(@Param("conversationId") String conversationId,
                                         @Param("userId") long userId);

    /**
     * Locks one active owned Conversation for a short database mutation.
     *
     * @param conversationId stable Conversation identifier
     * @param userId authenticated owner ID
     * @return locked Conversation, or {@code null} when absent
     */
    Conversation lockConversationByIdAndUser(@Param("conversationId") String conversationId, @Param("userId") long userId);

    /**
     * Tests whether an active Conversation belongs to the supplied owner.
     *
     * @param conversationId stable Conversation identifier
     * @param userId authenticated owner ID
     * @return {@code true} when the owned Conversation exists
     */
    boolean existsByIdAndUser(@Param("conversationId") String conversationId, @Param("userId") long userId);

    /**
     * Lists an owner-scoped navigation page using title and Group filters.
     *
     * @param userId authenticated owner ID
     * @param keyword nullable normalized title search
     * @param conversationGroupId nullable Group filter
     * @param includeGrouped whether a root query may include grouped Conversations
     * @param limit maximum rows to return
     * @param offset zero-based row offset
     * @return Conversations in navigation order
     */
    List<Conversation> listConversations(@Param("userId") long userId,
                                         @Param("keyword") String keyword,
                                         @Param("conversationGroupId") Long conversationGroupId,
                                         @Param("includeGrouped") boolean includeGrouped,
                                         @Param("limit") int limit,
                                         @Param("offset") int offset);

    /**
     * Counts rows matching the same owner and navigation filters as {@link #listConversations}.
     *
     * @param userId authenticated owner ID
     * @param keyword nullable normalized title search
     * @param conversationGroupId nullable Group filter
     * @param includeGrouped whether a root query may include grouped Conversations
     * @return matching active Conversation count
     */
    long countConversations(@Param("userId") long userId,
                            @Param("keyword") String keyword,
                            @Param("conversationGroupId") Long conversationGroupId,
                            @Param("includeGrouped") boolean includeGrouped);

    /**
     * Replaces the title of one active owned Conversation.
     *
     * @param conversationId stable Conversation identifier
     * @param userId authenticated owner ID
     * @param title normalized replacement title
     * @return updated Conversation, or {@code null} when absent
     */
    Conversation updateConversationTitle(@Param("conversationId") String conversationId, @Param("userId") long userId, @Param("title") String title);

    /**
     * Logically deletes one active owned Conversation.
     *
     * @param conversationId stable Conversation identifier
     * @param userId authenticated owner ID
     * @return affected row count
     */
    int deleteConversation(@Param("conversationId") String conversationId, @Param("userId") long userId);

    /**
     * Logically deletes a validated batch of owned Conversations.
     *
     * @param conversationIds stable Conversation identifiers
     * @param userId authenticated owner ID
     * @return affected row count
     */
    int deleteConversations(@Param("conversationIds") Collection<String> conversationIds, @Param("userId") long userId);

    /**
     * Logically deletes every active Conversation assigned to one owned Group.
     *
     * @param groupId database Group identity
     * @param userId authenticated owner ID
     * @return affected row count
     */
    int deleteConversationsByGroupId(@Param("groupId") long groupId, @Param("userId") long userId);

    /**
     * Moves every active Conversation out of a Group without deleting it.
     *
     * @param groupId database Group identity
     * @param userId authenticated owner ID
     * @return affected row count
     */
    int clearConversationGroupByGroupId(@Param("groupId") long groupId, @Param("userId") long userId);

    /**
     * Lists stable identifiers of active Conversations assigned to one owned Group.
     *
     * @param groupId database Group identity
     * @param userId authenticated owner ID
     * @return stable Conversation identifiers
     */
    List<String> listConversationIdsByGroupId(@Param("groupId") long groupId, @Param("userId") long userId);

    /**
     * Validates an entire batch without loading the Conversation rows.
     *
     * @param userId authenticated owner ID
     * @param conversationIds stable Conversation identifiers
     * @return {@code true} only when every requested Conversation exists and is owned
     */
    boolean allOwnedConversationsExist(@Param("userId") long userId, @Param("conversationIds") Collection<String> conversationIds);

    /**
     * Validates that every requested Conversation currently belongs to the supplied owned Group.
     *
     * @param userId authenticated owner ID
     * @param conversationGroupId database Group identity
     * @param conversationIds stable Conversation identifiers
     * @return {@code true} only when the complete batch belongs to that Group
     */
    boolean allOwnedConversationsBelongToGroup(@Param("userId") long userId,
                                               @Param("conversationGroupId") long conversationGroupId,
                                               @Param("conversationIds") Collection<String> conversationIds);

    /**
     * Validates that every requested owned Conversation is currently ungrouped.
     *
     * @param userId authenticated owner ID
     * @param conversationIds stable Conversation identifiers
     * @return {@code true} only when the complete batch is active and ungrouped
     */
    boolean allOwnedUngroupedConversationsExist(@Param("userId") long userId, @Param("conversationIds") Collection<String> conversationIds);

    /**
     * Applies one pin value to a validated batch of root Conversations.
     *
     * @param userId authenticated owner ID
     * @param conversationIds stable Conversation identifiers
     * @param pinned replacement pin state
     * @return affected row count
     */
    int updateConversationPinnedByIds(@Param("userId") long userId, @Param("conversationIds") Collection<String> conversationIds, @Param("pinned") boolean pinned);

    /**
     * Clears pin state before moving selected Conversations into a Group.
     *
     * @param userId authenticated owner ID
     * @param conversationIds stable Conversation identifiers
     * @return affected row count
     */
    int clearConversationPinnedByIds(@Param("userId") long userId, @Param("conversationIds") Collection<String> conversationIds);

    /**
     * Clears all root pin state for one owner.
     *
     * @param userId authenticated owner ID
     * @return affected row count
     */
    int clearConversationPinned(@Param("userId") long userId);

    /**
     * Moves a validated batch directly to one Group or back to the root list.
     *
     * @param userId authenticated owner ID
     * @param conversationIds stable Conversation identifiers
     * @param targetConversationGroupId target Group ID, or {@code null} for root
     * @return affected row count
     */
    int moveConversations(@Param("userId") long userId,
                          @Param("conversationIds") Collection<String> conversationIds,
                          @Param("targetConversationGroupId") Long targetConversationGroupId);

    /**
     * Removes only Conversations that currently belong to the supplied Group.
     *
     * @param userId authenticated owner ID
     * @param conversationGroupId current Group identity
     * @param conversationIds stable Conversation identifiers
     * @return affected row count
     */
    int removeConversationsFromGroup(@Param("userId") long userId,
                                     @Param("conversationGroupId") long conversationGroupId,
                                     @Param("conversationIds") Collection<String> conversationIds);

    /**
     * Batch-loads owned Conversations while preserving SQL-level owner filtering.
     *
     * @param conversationIds stable Conversation identifiers
     * @param userId authenticated owner ID
     * @return matching active Conversation rows
     */
    List<Conversation> listConversationsByIdsAndUser(@Param("conversationIds") Collection<String> conversationIds,
                                                     @Param("userId") long userId);

    /**
     * Replaces the high-water mark during a trusted migration or repair path.
     *
     * @param conversationId stable Conversation identifier
     * @param userId authenticated owner ID
     * @param roundNumber replacement high-water value
     * @return affected row count
     */
    int updateLatestRoundNumber(@Param("conversationId") String conversationId,
                                @Param("userId") long userId,
                                @Param("roundNumber") long roundNumber);

    /**
     * Atomically advances Round high-water state and derives the first automatic title when eligible.
     *
     * @param conversationId stable Conversation identifier
     * @param userId authenticated owner ID
     * @param roundNumber newly committed Round number
     * @param automaticTitle deterministic title derived from the first request
     * @param defaultTitle exact placeholder title that may be replaced
     * @return affected row count
     */
    int advanceLatestRoundNumber(@Param("conversationId") String conversationId,
                                 @Param("userId") long userId,
                                 @Param("roundNumber") long roundNumber,
                                 @Param("automaticTitle") String automaticTitle,
                                 @Param("defaultTitle") String defaultTitle);
}
