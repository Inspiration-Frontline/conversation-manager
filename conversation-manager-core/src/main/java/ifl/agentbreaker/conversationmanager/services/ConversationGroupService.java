package ifl.agentbreaker.conversationmanager.services;

import ifl.agentbreaker.authcenter.session.UserContextService;
import ifl.agentbreaker.conversationmanager.dao.ConversationGroupMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationMapper;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.AddConversationToGroupRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.CreateConversationGroupRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.DeleteConversationGroupRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.MoveConversationsRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.RemoveConversationFromGroupRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.ReorderConversationGroupsRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.UpdateConversationGroupAbstractRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.ConversationGroupAbstract;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationGroup;
import ifl.agentbreaker.conversationmanager.services.files.ConversationFileService;
import ifl.agentbreaker.conversationmanager.support.BusinessIdManager;
import ifl.agentbreaker.conversationmanager.support.TextNormalizer;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import stark.dataworks.boot.autoconfig.web.LogArgumentsAndResponse;
import stark.dataworks.boot.web.ServiceResponse;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@Validated
@LogArgumentsAndResponse
public class ConversationGroupService
{
    private static final int ERROR_GROUP_NOT_FOUND = 2102;
    private static final int ERROR_INVALID_CONVERSATION = 2103;
    private static final int ERROR_INVALID_GROUP_ORDER = 2104;
    private static final int MAX_GROUP_NAME_LENGTH = 100;

    @Autowired
    private ConversationMapper conversationMapper;

    @Autowired
    private ConversationFileService conversationFileService;

    @Autowired
    private ConversationGroupMapper conversationGroupMapper;

    /**
     * Creates a Group at the top of the current user's manual order.
     *
     * @param request validated Group name and optional description
     * @return persisted Group summary with no Conversations
     */
    @Transactional(rollbackFor = Exception.class)
    public ServiceResponse<ConversationGroupAbstract> createConversationGroup(@Valid CreateConversationGroupRequest request)
    {
        long userId = UserContextService.getCurrentUserId();
        conversationGroupMapper.acquireUserGroupLock(userId);
        conversationGroupMapper.incrementConversationGroupSortOrders(userId);

        ConversationGroup group = new ConversationGroup();
        group.setCreatorId(userId);
        group.setModifierId(userId);
        group.setGroupId(BusinessIdManager.newConversationGroupId());
        group.setName(TextNormalizer.trimToMaxLength(request.getName(), MAX_GROUP_NAME_LENGTH));
        group.setDescription(TextNormalizer.trimToNull(request.getDescription()));
        group.setSortOrder(1);
        group.setConversationCount(0);
        conversationGroupMapper.insertConversationGroup(group);

        return ServiceResponse.buildSuccessResponse(toConversationGroupAbstract(group));
    }

    /**
     * Updates one owned Group's display metadata without changing its order or membership.
     *
     * @param request owned Group ID and replacement fields
     * @return updated summary, or a not-found response
     */
    @Transactional(rollbackFor = Exception.class)
    public ServiceResponse<ConversationGroupAbstract> updateConversationGroupAbstract(
        @Valid UpdateConversationGroupAbstractRequest request)
    {
        long userId = UserContextService.getCurrentUserId();
        ConversationGroup group = conversationGroupMapper.lockConversationGroupByIdForUser(request.getGroupId(), userId);
        if (group == null)
            return ServiceResponse.buildErrorResponse(ERROR_GROUP_NOT_FOUND, "Conversation group does not exist.");

        if (StringUtils.hasText(request.getName()))
            group.setName(TextNormalizer.trimToMaxLength(request.getName(), MAX_GROUP_NAME_LENGTH));
        if (request.getDescription() != null)
            group.setDescription(TextNormalizer.trimToNull(request.getDescription()));

        group.setModifierId(userId);
        conversationGroupMapper.updateConversationGroupAbstract(group);
        return ServiceResponse.buildSuccessResponse(toConversationGroupAbstract(group));
    }

    /**
     * Persists one complete, duplicate-free ordering of all Groups owned by the caller.
     *
     * @param request complete ordered Group ID set
     * @return owner-scoped Group summaries in their new order
     */
    @Transactional(rollbackFor = Exception.class)
    public ServiceResponse<List<ConversationGroupAbstract>> reorderConversationGroups(
        @Valid ReorderConversationGroupsRequest request)
    {
        long userId = UserContextService.getCurrentUserId();
        conversationGroupMapper.acquireUserGroupLock(userId);

        List<String> requestedIds = BusinessIdManager.normalizeIds(request.getConversationGroupIds());
        List<String> ownedIds = conversationGroupMapper.listConversationGroupIdsForUpdate(userId);
        Set<String> requestedSet = new HashSet<>(requestedIds);
        if (requestedIds.size() != request.getConversationGroupIds().size()
            || requestedSet.size() != requestedIds.size()
            || requestedSet.size() != ownedIds.size()
            || !requestedSet.containsAll(ownedIds))
        {
            return ServiceResponse.buildErrorResponse(
                ERROR_INVALID_GROUP_ORDER,
                "Conversation group order must contain every owned group exactly once.");
        }

        int sortOrder = 1;
        for (String groupId : requestedIds)
            conversationGroupMapper.updateConversationGroupSortOrder(groupId, userId, sortOrder++);

        return getConversationGroupsOfUser();
    }

    /**
     * Deletes an owned Group while either preserving or deleting its Conversations atomically.
     *
     * @param request Group ID and explicit delete-children choice
     * @return true after the mutation and file-reference cleanup commit
     */
    @Transactional(rollbackFor = Exception.class)
    public ServiceResponse<Boolean> deleteConversationGroup(@Valid DeleteConversationGroupRequest request)
    {
        long userId = UserContextService.getCurrentUserId();
        conversationGroupMapper.acquireUserGroupLock(userId);
        ConversationGroup group = conversationGroupMapper.lockConversationGroupByIdForUser(request.getGroupId(), userId);
        if (group == null)
            return ServiceResponse.buildErrorResponse(ERROR_GROUP_NOT_FOUND, "Conversation group does not exist.");

        if (request.isDeleteConversations())
        {
            List<String> conversationIds = conversationMapper.listConversationIdsByGroupId(request.getGroupId(), userId);
            conversationMapper.deleteConversationsByGroupId(request.getGroupId(), userId);
            conversationFileService.releaseConversationReferences(conversationIds, userId);
        }
        else
            conversationMapper.clearConversationGroupByGroupId(request.getGroupId(), userId);

        conversationGroupMapper.deleteConversationGroup(request.getGroupId(), userId);
        return ServiceResponse.buildSuccessResponse(true);
    }

    /**
     * Lists all Groups with active Conversation counts in manual order.
     *
     * @return owner-scoped Group summaries
     */
    public ServiceResponse<List<ConversationGroupAbstract>> getConversationGroupsOfUser()
    {
        long userId = UserContextService.getCurrentUserId();
        List<ConversationGroupAbstract> groups = conversationGroupMapper.listConversationGroups(userId)
            .stream()
            .map(this::toConversationGroupAbstract)
            .toList();
        return ServiceResponse.buildSuccessResponse(groups);
    }

    /**
     * Moves Conversations directly to another Group or back to the root list.
     *
     * @param request owned Conversation IDs and nullable target Group ID
     * @return true when every requested Conversation is moved
     */
    @Transactional(rollbackFor = Exception.class)
    public ServiceResponse<Boolean> moveConversations(@Valid MoveConversationsRequest request)
    {
        long userId = UserContextService.getCurrentUserId();
        conversationGroupMapper.acquireUserGroupLock(userId);

        String targetGroupId = TextNormalizer.trimToNull(request.getTargetConversationGroupId());
        if (targetGroupId != null
            && conversationGroupMapper.lockConversationGroupByIdForUser(targetGroupId, userId) == null)
            return ServiceResponse.buildErrorResponse(ERROR_GROUP_NOT_FOUND, "Conversation group does not exist.");

        List<String> conversationIds = BusinessIdManager.normalizeIds(request.getConversationIds());
        if (CollectionUtils.isEmpty(conversationIds)
            || !conversationMapper.allOwnedConversationsExist(userId, conversationIds))
            return ServiceResponse.buildErrorResponse(ERROR_INVALID_CONVERSATION, "Some conversations do not exist.");

        conversationMapper.moveConversations(userId, conversationIds, targetGroupId);
        return ServiceResponse.buildSuccessResponse(true);
    }

    /**
     * Compatibility entry point that now performs one direct move instead of creating relation rows.
     *
     * @param request target Group and owned Conversation IDs
     * @return true after the move
     */
    @Transactional(rollbackFor = Exception.class)
    public ServiceResponse<Boolean> addConversationsToGroup(@Valid AddConversationToGroupRequest request)
    {
        MoveConversationsRequest moveRequest = new MoveConversationsRequest();
        moveRequest.setConversationIds(request.getConversationIds());
        moveRequest.setTargetConversationGroupId(request.getConversationGroupId());
        return moveConversations(moveRequest);
    }

    /**
     * Removes Conversations only when they currently belong to the supplied owned Group.
     *
     * @param request source Group and Conversation IDs
     * @return true after the Conversations return to the root list
     */
    @Transactional(rollbackFor = Exception.class)
    public ServiceResponse<Boolean> removeConversationsFromGroup(@Valid RemoveConversationFromGroupRequest request)
    {
        long userId = UserContextService.getCurrentUserId();
        if (conversationGroupMapper.lockConversationGroupByIdForUser(request.getConversationGroupId(), userId) == null)
            return ServiceResponse.buildErrorResponse(ERROR_GROUP_NOT_FOUND, "Conversation group does not exist.");

        List<String> conversationIds = BusinessIdManager.normalizeIds(request.getConversationIds());
        if (CollectionUtils.isEmpty(conversationIds)
            || !conversationMapper.allOwnedConversationsBelongToGroup(
                userId, request.getConversationGroupId(), conversationIds))
            return ServiceResponse.buildErrorResponse(ERROR_INVALID_CONVERSATION, "Some conversations are not in this group.");

        conversationMapper.removeConversationsFromGroup(userId, request.getConversationGroupId(), conversationIds);
        return ServiceResponse.buildSuccessResponse(true);
    }

    private ConversationGroupAbstract toConversationGroupAbstract(ConversationGroup group)
    {
        ConversationGroupAbstract groupAbstract = new ConversationGroupAbstract();
        BeanUtils.copyProperties(group, groupAbstract);
        return groupAbstract;
    }
}
