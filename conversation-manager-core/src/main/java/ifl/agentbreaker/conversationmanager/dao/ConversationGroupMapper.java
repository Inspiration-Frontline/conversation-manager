package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationGroup;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.ConversationGroupAbstract;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** MyBatis persistence operations scoped to Conversation groups and their ordering. */
@Mapper
public interface ConversationGroupMapper
{
    /** Inserts one owned Conversation group.
     * @param group initialized group entity
     * @return affected row count
     */
    int insertConversationGroup(ConversationGroup group);

    /** Loads one group after applying the owner predicate.
     * @param groupId database group identity
     * @param userId trusted authenticated owner ID
     * @return owned group, or {@code null} when absent
     */
    ConversationGroup getConversationGroupByIdForUser(@Param("groupId") long groupId, @Param("userId") long userId);

    /** Locks one owned group for an ordering mutation.
     * @param groupId database group identity
     * @param userId trusted authenticated owner ID
     * @return locked group, or {@code null} when absent
     */
    ConversationGroup lockConversationGroupByIdForUser(@Param("groupId") long groupId, @Param("userId") long userId);

    /** Tests whether a group belongs to the current owner.
     * @param groupId database group identity
     * @param userId trusted authenticated owner ID
     * @return {@code true} when the owned group exists
     */
    boolean existsByIdAndUser(@Param("groupId") long groupId, @Param("userId") long userId);

    /** Updates mutable group display metadata.
     * @param group owned group carrying normalized replacement values
     * @return affected row count
     */
    int updateConversationGroupAbstract(ConversationGroup group);

    /** Logically deletes one owned group.
     * @param groupId database group identity
     * @param userId trusted authenticated owner ID
     * @return affected row count
     */
    int deleteConversationGroup(@Param("groupId") long groupId, @Param("userId") long userId);

    /** Lists the owner's groups in display order.
     * @param userId trusted authenticated owner ID
     * @return ordered group summaries
     */
    List<ConversationGroupAbstract> listConversationGroups(@Param("userId") long userId);

    /** Persists a complete in-memory group reorder as one batch.
     * @param groups groups carrying their new sort orders
     * @return affected row count
     */
    int batchUpdateConversationGroupSortOrders(@Param("groups") List<ConversationGroup> groups);

    /** Shifts existing group sort orders to make room at the beginning.
     * @param userId trusted authenticated owner ID
     * @return affected row count
     */
    int incrementConversationGroupSortOrders(@Param("userId") long userId);

    /** Locks and lists every group participating in an owner reorder.
     * @param userId trusted authenticated owner ID
     * @return locked groups in current display order
     */
    List<ConversationGroup> listConversationGroupsForUpdate(@Param("userId") long userId);

    /** Acquires the database advisory row used to serialize one owner's group mutations.
     * @param userId trusted authenticated owner ID
     */
    void acquireUserGroupLock(@Param("userId") long userId);
}
